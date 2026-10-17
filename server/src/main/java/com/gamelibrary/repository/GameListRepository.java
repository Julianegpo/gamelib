package com.gamelibrary.repository;

import com.gamelibrary.domain.GameList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GameListRepository extends JpaRepository<GameList, UUID> {

    List<GameList> findByUsername(String username);
}
