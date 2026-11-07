package com.scentstation.identity.tenant;

import jakarta.persistence.*;

@Entity
@Table(name = "brand_tenants", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Tenant {
    public enum Status { ACTIVE, SUSPENDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64, unique = true)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 1024)
    private String logoUrl;
    @Column(length = 2000)
    private String description;
    @Column(length = 160)
    private String contactEmail;
    @Column(length = 40)
    private String contactPhone;
    @Column(length = 3000)
    private String kioskContent;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    protected Tenant() {}
    public Tenant(String code, String name) { this.code = code; this.name = name; }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLogoUrl() { return logoUrl; }
    public String getDescription() { return description; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getKioskContent() { return kioskContent; }
    public Status getStatus() { return status; }
    public void setName(String name) { this.name = name; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public void setDescription(String description) { this.description = description; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public void setKioskContent(String kioskContent) { this.kioskContent = kioskContent; }
    public void setStatus(Status status) { this.status = status; }
}
