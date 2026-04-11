package ai.javaclaw.memory;

import java.util.List;
import java.util.Optional;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private final MemoryRepository repository;

    public MemoryService(MemoryRepository repository) {
        this.repository = repository;
    }

    public Memory store(@Nullable String ownerId, String key, String content, @Nullable String category) {
        Optional<Memory> existing = findByKey(ownerId, key);
        if (existing.isPresent()) {
            Memory updated = existing.get().withContent(content);
            if (category != null) {
                updated = updated.withCategory(category);
            }
            return repository.save(updated);
        }
        return repository.save(Memory.create(ownerId, key, content, category));
    }

    public Optional<Memory> recall(@Nullable String ownerId, String key) {
        Optional<Memory> userMemory = findByKey(ownerId, key);
        if (userMemory.isPresent()) {
            return userMemory;
        }
        // fallback to global
        if (ownerId != null) {
            return repository.findByOwnerIdIsNullAndKey(key);
        }
        return Optional.empty();
    }

    public boolean forget(@Nullable String ownerId, String key) {
        Optional<Memory> existing = findByKey(ownerId, key);
        if (existing.isEmpty()) {
            return false;
        }
        if (ownerId != null) {
            repository.deleteByOwnerIdAndKey(ownerId, key);
        } else {
            repository.deleteByOwnerIdIsNullAndKey(key);
        }
        return true;
    }

    public List<Memory> list(@Nullable String ownerId) {
        if (ownerId != null) {
            return repository.findAllByOwnerId(ownerId);
        }
        return repository.findAllByOwnerIdIsNull();
    }

    public List<Memory> search(@Nullable String ownerId, String query) {
        if (ownerId != null) {
            return repository.searchByOwner(ownerId, query);
        }
        return repository.searchGlobal(query);
    }

    private Optional<Memory> findByKey(@Nullable String ownerId, String key) {
        if (ownerId != null) {
            return repository.findByOwnerIdAndKey(ownerId, key);
        }
        return repository.findByOwnerIdIsNullAndKey(key);
    }
}
