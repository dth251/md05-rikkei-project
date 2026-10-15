window.onload = function () {
  window.ui = SwaggerUIBundle({
    url: "./api-chinh.json",
    dom_id: "#swagger-ui",
    presets: [SwaggerUIBundle.presets.apis],
    layout: "BaseLayout",
    deepLinking: true,
    filter: true,
    docExpansion: "none",
    defaultModelsExpandDepth: -1,
    displayRequestDuration: true,
    persistAuthorization: false,
    queryConfigEnabled: false,
    validatorUrl: null
  });
};
