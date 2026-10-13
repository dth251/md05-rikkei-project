package com.app.payment.service.impl;

import com.app.payment.config.VnpaySettings;
import com.app.payment.dto.request.VnpayLedgerRequest;
import com.app.payment.dto.response.VnpayLedgerResponse;
import com.app.payment.security.VnpayLedgerSignature;
import com.app.payment.security.VnpaySignature;
import com.app.payment.service.VnpayLedgerClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VnpayLedgerClientImpl implements VnpayLedgerClient {

    private static final String SANDBOX_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private final URI endpoint;
    private final VnpaySettings settings;
    private final ObjectMapper json;
    private final Semaphore concurrency = new Semaphore(4);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public VnpayLedgerClientImpl(
            VnpaySettings settings,
            ObjectMapper json,
            @Value("${payment.vnpay.ledger-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String url,
            @Value("${payment.vnpay.allow-local-fixture:false}") boolean localFixture
    ) {
        this.settings = settings;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        URI uri;
        try {
            uri = URI.create(url);
            boolean loopback = "http".equals(uri.getScheme()) && ("127.0.0.1".equals(uri.getHost())
                    || "[::1]".equals(uri.getHost())) && uri.getPort() > 0;
            if ((!SANDBOX_URL.equals(url) && !(localFixture && loopback)) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("VNPay sandbox ledger endpoint required");
        }
        endpoint = uri;
    }

    @Override
    public VnpayLedgerResponse execute(VnpayLedgerRequest request) {
        var parameters = new LinkedHashMap<>(request.parameters());
        parameters.put("vnp_SecureHash", VnpaySignature.hmac(VnpayLedgerSignature.requestData(parameters), settings.secret()));
        if (!concurrency.tryAcquire()) {
            throw unavailable();
        }
        try {
            var http = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(parameters))).build();
            var future = client.sendAsync(http, ignored -> new BoundedBodySubscriber());
            HttpResponse<byte[]> response;
            try {
                // The deadline includes the response body, rather than only receipt of headers.
                response = future.copy().orTimeout(6, java.util.concurrent.TimeUnit.SECONDS).join();
            } catch (CompletionException exception) {
                future.cancel(true);
                throw unavailable();
            }
            byte[] body = response.body();
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            var node = json.readTree(body);
            if (node == null || !node.isObject() || node.size() > 32) {
                throw unavailable();
            }
            var result = new LinkedHashMap<String, String>();
            var fields = node.properties().iterator();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!field.getKey().matches("vnp_[A-Za-z]{1,40}") || !field.getValue().isTextual()
                        || field.getValue().textValue().length() > 512) {
                    throw unavailable();
                }
                result.put(field.getKey(), field.getValue().textValue());
            }
            if (!VnpayLedgerSignature.verifyResponse(result, parameters.get("vnp_Command"), settings.secret())) {
                throw unavailable();
            }
            return new VnpayLedgerResponse(result);
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable();
        } finally {
            concurrency.release();
        }
    }

    private IllegalStateException unavailable() {
        // Avoid retaining an exception cause containing URLs, signed payloads or private configuration.
        return new IllegalStateException("VNPay ledger result unavailable");
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 16384) {
                    subscription.cancel();
                    body.completeExceptionally(new IllegalStateException("Provider response too large"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(new IllegalStateException("Provider response unavailable"));
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
