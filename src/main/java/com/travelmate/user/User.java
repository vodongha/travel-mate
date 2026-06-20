package com.travelmate.user;

import com.travelmate.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/** A user account (SPEC §6 Module 1). Email is partial-unique among live rows. */
@Entity
@Table(name = "USERS")
@SQLRestriction("IS_DELETED = 0")
public class User extends BaseEntity {

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    /** BCrypt hash; null for Google-only accounts (Oracle stores '' as NULL, so never write ""). */
    @Column(name = "PASSWORD_HASH", length = 255)
    private String passwordHash;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "AVATAR", length = 500)
    private String avatar;

    @Column(name = "TIMEZONE", nullable = false, length = 64)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "DEFAULT_CURRENCY", nullable = false, length = 3)
    private String defaultCurrency = "VND";

    @Column(name = "EMAIL_VERIFIED", nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "PROVIDER", nullable = false, length = 20)
    private AuthProvider provider;

    public User() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider;
    }
}
