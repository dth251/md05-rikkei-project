package com.app.auth.repository;

import com.app.auth.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findAllByUser_IdOrderByIdAsc(Long userId);

    Optional<Address> findByIdAndUser_Id(Long id, Long userId);
}
