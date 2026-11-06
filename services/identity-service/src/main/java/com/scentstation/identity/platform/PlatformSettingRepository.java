package com.scentstation.identity.platform;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
    Optional<PlatformSetting> findByScopeIdAndKey(String scopeId, String key);
}
