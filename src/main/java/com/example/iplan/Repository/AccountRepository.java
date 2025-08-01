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

    public PendingAccountRequest findExistingRequest(String childEncryptedNickname, String parentEncryptedNickname, Boolean approved, String status)
        throws ExecutionException, InterruptedException {

        Map<String, Object> filters = Map.of(
                "childEncryptedNickname", childEncryptedNickname,
                "parentEncryptedNickname", parentEncryptedNickname,
                "approved", approved,
                "status", status
        );
        return findByFields(filters);
    }

    // 부모가 보낸 요청이 있는지 확인 -> status 여러개 조건 포함
    public PendingAccountRequest findParentRequestByStatuses(String parentEncryptedNickname, List<String> statuses)
            throws ExecutionException, InterruptedException {

        return findByFieldAndInList("parentEncryptedNickname", parentEncryptedNickname, "status", statuses);
    }

    // PendingAccountRequest 문서 ID로 요청 반환
    public PendingAccountRequest findByRequestId(String docId)
        throws ExecutionException, InterruptedException {
        return findEntityByDocumentId(docId);
    }

    /**
     * DTO 변환
     */
    public AccountRequestDTO convertToDTO(PendingAccountRequest entity) {
        return AccountRequestDTO.builder()
                .id(entity.getId())
                .childNickname(entity.getChildEncryptedNickname())
                .parentNickname(entity.getParentEncryptedNickname())
                .approved(entity.isApproved())
                .build();
    }
}
