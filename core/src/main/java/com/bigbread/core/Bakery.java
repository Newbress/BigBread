package com.bigbread.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "bakery")
public class Bakery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id")
    private District district;

    private String roadAddress;

    private String jibunAddress;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    private String phone;

    private String kakaoPlaceId;

    private String publicDataId;

    private String instagramUsername;

    @Column(nullable = false)
    private double popularityScore;

    @Column(nullable = false)
    private String status = "ACTIVE";

    private Instant lastSyncedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Bakery() {
    }

    public Bakery(String name, District district, Point location) {
        this.name = name;
        this.district = district;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public District getDistrict() {
        return district;
    }

    public Point getLocation() {
        return location;
    }

    public double getPopularityScore() {
        return popularityScore;
    }

    public String getStatus() {
        return status;
    }
}
