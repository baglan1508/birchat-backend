package kz.birchat.api.repository;

import kz.birchat.api.entity.AuthCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthCodeRepository extends JpaRepository<AuthCodeEntity, UUID> {

    Optional<AuthCodeEntity> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);

    Optional<AuthCodeEntity> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    List<AuthCodeEntity> findByPhoneAndConsumedFalse(String phone);
}