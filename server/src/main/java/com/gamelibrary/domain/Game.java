package com.gamelibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "platform", nullable = false, length = 50)
    private String platform;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    @Column(name = "description")
    private String description;

    protected Game() {
        // JPA needs a no-arg constructor
    }

    public Game(String id, String title, String platform, int releaseYear, String description) {
        this.id = id;
        this.title = title;
        this.platform = platform;
        this.releaseYear = releaseYear;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public int getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(int releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
