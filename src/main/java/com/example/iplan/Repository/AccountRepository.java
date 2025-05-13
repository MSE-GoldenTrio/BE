package com.example.iplan.Repository;

import com.example.iplan.DTO.AccountRequestDTO;
import com.example.iplan.Domain.PendingAccountRequest;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class AccountRepository extends DefaultFirebaseDBRepository<PendingAccountRequest> {
    public AccountRepository(Firestore firestore) {
        super(firestore);
        setEntityClass(PendingAccountRequest.class);
        setCollectionName("PendingAccountRequest");
    }

    public List<PendingAccountRequest> findByChildNicknameAndApproved(String childNickname, boolean approved)
            throws ExecutionException, InterruptedException {

        Map<String, Object> filters = Map.of(
                "childNickname", childNickname,
                "approved", approved
        );

        return findAllByFields(filters);
    }

    /**
     * DTO 변환
     */
    public AccountRequestDTO convertToDTO(PendingAccountRequest entity) {
        return AccountRequestDTO.builder()
                .childNickname(entity.getChildNickname())
                .parentNickname(entity.getParentNickname())
                .approved(entity.isApproved())
                .build();
    }
}
