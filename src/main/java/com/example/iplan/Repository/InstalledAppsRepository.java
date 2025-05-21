package com.example.iplan.Repository;

import com.example.iplan.Domain.InstalledApps;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@Repository
public class InstalledAppsRepository extends DefaultFirebaseDBRepository<InstalledApps> {
    public InstalledAppsRepository(Firestore firestore){
        super(firestore);
        setEntityClass(InstalledApps.class);
        setCollectionName("InstalledApps");
    }

    public InstalledApps findByUserId(String user_id) throws ExecutionException, InterruptedException {
        Map<String, Object> filters = Map.of(
                "user_id", user_id
        );
        return findByFields(filters);
    }
}
