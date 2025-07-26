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

    public Optional<Users> findByEncryptedEmail(String email) {
        try {
            Users user = findByField("email", email); // 이메일을 기반으로 조회
            return Optional.ofNullable(user);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Optional<Users> findByHashValueEmail(String emailHash) {
        try {
            Users user = findByFields(Map.of("emailHash", emailHash)); // 아이디(닉네임) 기반으로 조회
            return Optional.ofNullable(user);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Optional<Users> findByNickname(String nickname) {
        try {
            Users user = findByField("nickname", nickname); // 아이디(닉네임) 기반으로 조회
            return Optional.ofNullable(user);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Optional<Users> findByHashValueNickName(String nicknameHash) {
        try {
            Users user = findByFields(Map.of("nicknameHash", nicknameHash)); // 아이디(닉네임) 기반으로 조회
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

    public boolean isLinkedToChild(String parentUid, String childUid) {
        try {
            Users parent = findByField("nickname", parentUid);
            if (parent != null && parent.getLinked_id() != null) {
                return parent.getLinked_id().contains(childUid);
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

}
