package com.example.iplan.auth;

import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class UserRepository extends DefaultFirebaseDBRepository<Users> {
    public UserRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(Users.class);
        setCollectionName("User");
    }

    public Optional<Users> findByEmail(String email) {
        try {
            Users user = findByFields(Map.of("email", email)); // 이메일을 기반으로 조회
            return Optional.ofNullable(user);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Optional<Users> findById(String userId) {
        try {
            return Optional.ofNullable(findEntityByDocumentId(userId));
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

}
