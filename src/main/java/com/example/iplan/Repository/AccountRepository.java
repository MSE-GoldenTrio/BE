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

    public List<PendingAccountRequest> findByChildNicknameAndApprovedAndStatus(String childNickname, boolean approved, String status)
            throws ExecutionException, InterruptedException {

        Map<String, Object> filters = Map.of(
                "childNickname", childNickname,
                "approved", approved,
                "status", status
        );

        return findAllByFields(filters);
    }

    // 수락되지 않은 동일한 요청이 이미 존재하는지 확인
    public PendingAccountRequest findExistingRequest(String childNickname, String parentNickname)
        throws ExecutionException, InterruptedException {

        Map<String, Object> filters = Map.of(
                "childNickname", childNickname,
                "parentNickname", parentNickname,
                "approved", false,
                "status", "pending"
        );
        return findByFields(filters);
    }

    // 이미 해당 계정과 연동이 되어있는지 확인
    public PendingAccountRequest findApprovedRequest(String childNickname, String parentNickname)
        throws ExecutionException, InterruptedException {

        Map<String, Object> filters = Map.of(
                "childNickname", childNickname,
                "parentNickname", parentNickname,
                "approved", true,
                "status", "approved"
        );
        return findByFields(filters);
    }
    /**
     * DTO 변환
     */
    public AccountRequestDTO convertToDTO(PendingAccountRequest entity) {
        return AccountRequestDTO.builder()
                .id(entity.getId())
                .childNickname(entity.getChildNickname())
                .parentNickname(entity.getParentNickname())
                .approved(entity.isApproved())
                .build();
    }
}
