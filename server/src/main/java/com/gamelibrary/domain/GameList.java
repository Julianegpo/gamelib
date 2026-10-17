package com.gamelibrary.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "game_lists")
public class GameList {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "game_list_games", joinColumns = @JoinColumn(name = "list_id"))
    @Column(name = "game_id", nullable = false, length = 100)
    private Set<String> gameIds = new LinkedHashSet<>();

    protected GameList() {
        // JPA needs a no-arg constructor
    }

    public GameList(UUID id, String username, String name, String description, Set<String> gameIds) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.description = description;
        this.gameIds = new LinkedHashSet<>(gameIds);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getGameIds() {
        return gameIds;
    }
}
