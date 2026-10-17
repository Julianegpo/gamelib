package com.gamelibrary.repository;

import com.gamelibrary.domain.LibraryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, LibraryEntry.Key> {

    List<LibraryEntry> findByUsername(String username);
}
