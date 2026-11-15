package kz.birchat.api.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, UUID> {

    Optional<DeviceTokenEntity> findByToken(String token);

    List<DeviceTokenEntity> findByUserIdIn(Collection<UUID> userIds);

    @Modifying
    @Query("DELETE FROM DeviceTokenEntity d WHERE d.token = :token")
    int deleteByTokenValue(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM DeviceTokenEntity d WHERE d.userId = :userId")
    int deleteByUser(@Param("userId") UUID userId);
}
