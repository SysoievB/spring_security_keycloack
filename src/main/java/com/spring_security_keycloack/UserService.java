package com.spring_security_keycloack;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserService() {
        // Initialize with sample data
        initializeUsers();
    }

    private void initializeUsers() {
        User admin = new User("1", "admin", "admin@example.com", "Admin", "User", List.of("ADMIN"));
        User user1 = new User("2", "john_doe", "john@example.com", "John", "Doe", List.of("USER"));
        User user2 = new User("3", "jane_smith", "jane@example.com", "Jane", "Smith", List.of("USER"));

        users.put(admin.getId(), admin);
        users.put(user1.getId(), user1);
        users.put(user2.getId(), user2);
    }

    public Flux<User> getAllUsers() {
        return Flux.fromIterable(users.values());
    }

    public Mono<User> getUserById(String id) {
        return Mono.justOrEmpty(users.get(id));
    }

    //.doOnNext -> You’re not transforming the User, you’re just performing a side-effect (assigning ID and storing in a map).
    //The returned Mono<User> still emits the same User object, but you’ve also executed a side-effect.
    //Cleaner intention: "I want to perform an action when this event happens, but I’m not changing the flow."
    public Mono<User> createUser(User newUser) {
        return Mono.just(newUser)
                .doOnNext(user -> {
                    user.setId(String.valueOf(users.size() + 1));
                    users.put(user.getId(), user);
                });
    }

    //.map() implies "I am transforming the data", but in reality you’re doing a side effect.
    //It works because you return the same user, but semantically it’s misleading.
    //Future maintainers may assume map is safe/pure transformation, while you’re doing a DB write.
    public Mono<User> updateUser(String id, User user) {
        return Mono.justOrEmpty(users.get(id))
                .map(existingUser -> {
                    user.setId(id);
                    user.setCreatedAt(existingUser.getCreatedAt());
                    users.put(id, user);
                    return user;
                });
    }

    public Mono<Void> deleteUser(String id) {
        return Mono.fromRunnable(() -> users.remove(id));
    }

    public Flux<User> getUsersByRole(String role) {
        return getAllUsers()
                .filter(user -> user.getRoles().contains(role));
    }
}