package com.example.iplan.Repository;

import com.example.iplan.Domain.PlanCategory;
import com.example.iplan.Repository.DefaultFirebaseRepository.DefaultFirebaseDBRepository;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PlanCategoryRepository extends DefaultFirebaseDBRepository<PlanCategory> {

    public PlanCategoryRepository(Firestore firestore){
        super(firestore);
        setEntityClass(PlanCategory.class);
        setCollectionName("PlanCategory");
    }
}
