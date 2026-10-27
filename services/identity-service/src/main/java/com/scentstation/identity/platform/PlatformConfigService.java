package com.scentstation.identity.platform;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.audit.AuditEvent;
import com.scentstation.identity.audit.AuditEventRepository;
import com.scentstation.identity.rbac.RbacException;
import com.scentstation.identity.rbac.Role;
import com.scentstation.identity.tenant.TenantScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Map;

@Service
public class PlatformConfigService {
    public static final String GLOBAL = "GLOBAL";
    private static final Map<String,String> DEFAULTS = Map.of(
        "DEVICE_IDLE_TIMEOUT_SECONDS", "300",
        "ORDER_PAYMENT_TIMEOUT_SECONDS", "300",
        "MACHINE_HEARTBEAT_TIMEOUT_SECONDS", "60",
        "NOTIFICATION_ENABLED", "true",
        "NOTIFICATION_CHANNEL", "IN_APP"
    );
    private final PlatformSettingRepository settings;
    private final AuditEventRepository auditEvents;
    private final TenantScopeService scope;
    private final Clock clock;
    public PlatformConfigService(PlatformSettingRepository settings,TenantScopeService scope,Clock clock,AuditEventRepository auditEvents){
        this.settings=settings;this.scope=scope;this.clock=clock;this.auditEvents=auditEvents;
    }
    public record ConfigView(String scopeId,String key,String value,String source){}
    private void requireAdmin(AuthDto.Identity actor){
        if(actor.roles()==null || (!actor.roles().contains(Role.BRAND_ADMIN.name())
             && !actor.roles().contains(Role.PLATFORM_SUPER_ADMIN.name())))
            throw new RbacException(HttpStatus.FORBIDDEN,"CONFIG_PERMISSION_DENIED");
    }
    private void requirePlatform(AuthDto.Identity actor){
        if(!scope.isPlatformAdmin(actor))throw new RbacException(HttpStatus.FORBIDDEN,"CONFIG_PLATFORM_ONLY");
    }
    private void supported(String key){
        if(!DEFAULTS.containsKey(key))throw new RbacException(HttpStatus.BAD_REQUEST,"CONFIG_UNKNOWN_KEY");
    }
    private String checkValue(String key,String value){
        supported(key);
        if(value==null)throw new RbacException(HttpStatus.BAD_REQUEST,"CONFIG_INVALID_VALUE");
        String v=value.trim();
        if(v.length()>100)throw new RbacException(HttpStatus.BAD_REQUEST,"CONFIG_INVALID_VALUE");
        if(key.equals("NOTIFICATION_ENABLED")) {
            if(!v.equals("true")&&!v.equals("false"))throw new RbacException(HttpStatus.BAD_REQUEST,"CONFIG_INVALID_VALUE");
        }else if(key.equals("NOTIFICATION_CHANNEL")){
            if(!v.equals("IN_APP"))throw new RbacException(HttpStatus.BAD_REQUEST,"CONFIG_INVALID_VALUE");
        }else{
            try{
                if(!v.matches("[0-9]{1,5}"))throw new NumberFormatException();
                int n=Integer.parseInt(v);
                int min= key.equals("MACHINE_HEARTBEAT_TIMEOUT_SECONDS")?10:30;
                int max= key.equals("MACHINE_HEARTBEAT_TIMEOUT_SECONDS")?600:3600;
                if(n<min||n>max)throw new NumberFormatException();
                v=Integer.toString(n);
            }catch(NumberFormatException ex){throw new RbacException(HttpStatus.BAD_REQUEST,"CONFIG_INVALID_VALUE");}
        }
        return v;
    }
    @Transactional(readOnly=true)
    public ConfigView getGlobal(AuthDto.Identity actor,String key){
        requireAdmin(actor);supported(key);
        return new ConfigView(GLOBAL,key,settings.findByScopeIdAndKey(GLOBAL,key)
            .map(PlatformSetting::getValue).orElse(DEFAULTS.get(key)),
            settings.findByScopeIdAndKey(GLOBAL,key).isPresent()?"GLOBAL_OVERRIDE":"DEFAULT");
    }
    @Transactional
    public ConfigView putGlobal(AuthDto.Identity actor,String key,String value){
        requirePlatform(actor);String v=checkValue(key,value);
        upsert(GLOBAL,key,v,actor.userId());recordAudit(actor.userId(),GLOBAL,key);return new ConfigView(GLOBAL,key,v,"GLOBAL_OVERRIDE");
    }
    @Transactional(readOnly=true)
    public ConfigView getTenant(AuthDto.Identity actor,String tenantId,String key){
        requireAdmin(actor);scope.requireTenant(actor,tenantId);supported(key);
        return settings.findByScopeIdAndKey(tenantId,key)
           .map(setting -> new ConfigView(tenantId,key,setting.getValue(),"TENANT_OVERRIDE"))
           .orElseGet(()->settings.findByScopeIdAndKey(GLOBAL,key)
               .map(setting->new ConfigView(tenantId,key,setting.getValue(),"GLOBAL_OVERRIDE"))
               .orElseGet(()->new ConfigView(tenantId,key,DEFAULTS.get(key),"DEFAULT")));
    }
    @Transactional
    public ConfigView putTenant(AuthDto.Identity actor,String tenantId,String key,String value){
        requireAdmin(actor);scope.requireTenant(actor,tenantId);String v=checkValue(key,value);
        upsert(tenantId,key,v,actor.userId());recordAudit(actor.userId(),tenantId,key);return new ConfigView(tenantId,key,v,"TENANT_OVERRIDE");
    }
    @Transactional(readOnly=true)
    public boolean notificationsEnabledForTenant(String tenantId){
        String selected=settings.findByScopeIdAndKey(tenantId,"NOTIFICATION_ENABLED")
            .map(PlatformSetting::getValue)
            .orElseGet(()->settings.findByScopeIdAndKey(GLOBAL,"NOTIFICATION_ENABLED")
                .map(PlatformSetting::getValue).orElse("true"));
        return "true".equals(selected);
    }
    private void recordAudit(String actorId,String tenantId,String key){
        auditEvents.save(new AuditEvent(tenantId,actorId,"CONFIG_UPDATED","CONFIG",key,
            "SUCCESS",java.util.UUID.randomUUID().toString(),clock.instant()));
    }
    private void upsert(String scopeId,String key,String value,String actorId){
        PlatformSetting s=settings.findByScopeIdAndKey(scopeId,key).orElse(null);
        if(s==null)s=new PlatformSetting(scopeId,key,value,actorId,clock.instant());
        else s.update(value,actorId,clock.instant());
        settings.save(s);
    }
}
