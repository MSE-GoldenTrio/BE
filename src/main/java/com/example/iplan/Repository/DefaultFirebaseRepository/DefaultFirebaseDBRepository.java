package com.example.iplan.Repository.DefaultFirebaseRepository;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.firestore.annotation.DocumentId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
@Repository
@RequiredArgsConstructor
public class DefaultFirebaseDBRepository<T> implements FirebaseDBRepository<T, String> {

    private Class<T> entityClass;
    private String collectionName;

    protected final Firestore firestore;

    // Entity 클래스 설정 메서드
    public void setEntityClass(Class<T> entityClass){
        this.entityClass = entityClass;
    }

    // 컬렉션 이름 설정 메서드
    public void setCollectionName(String collectionName){
        this.collectionName = collectionName;
    }

    // Entity 저장 메서드
    @Override
    public void save(T entity) throws ExecutionException, InterruptedException {
        // 1. Firestore 컬렉션 참조 가져오기
        CollectionReference collection = firestore.collection(collectionName);

        // 2. 고유 Document ID 생성
        String documentId = UUID.randomUUID().toString();

        // 3. Firestore에 데이터 저장
        ApiFuture<WriteResult> result = collection.document(documentId).set(entity);
        result.get(); // 저장이 완료될 때까지 대기

        // 4. 엔티티의 @DocumentId 필드에 값 설정
        setDocumentIdField(entity, documentId);
    }

    // @DocumentId 필드에 ID를 설정하는 헬퍼 메서드
    private void setDocumentIdField(T entity, String documentId) {
        Field[] fields = entity.getClass().getDeclaredFields(); // 엔티티 클래스의 모든 필드 가져오기
        for (Field field : fields) {
            if (field.isAnnotationPresent(DocumentId.class)) { // @DocumentId 애노테이션이 있는지 확인
                field.setAccessible(true); // private 필드에 접근 가능하도록 설정
                try {
                    field.set(entity, documentId); // @DocumentId 필드에 값 설정
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to set @DocumentId field", e);
                }
                break; // 첫 번째 @DocumentId 필드만 처리
            }
        }
    }

    // Entity 업데이트 메서드
    @Override
    public void update(T entity) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionName);
        ApiFuture<WriteResult> result = collection.document(getDocumentId(entity)).set(entity);
        result.get();
    }

    // Entity 삭제 메서드
    @Override
    public void delete(T entity) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionName);
        ApiFuture<WriteResult> result = collection.document(getDocumentId(entity)).delete();
        result.get();
    }

    /**
     * 문서 아이디로 고유 문서를 찾는 메서드
     * 이는 user_id도 필요 없다.
     * @param document_id
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Override
    public T findEntityByDocumentId(String document_id) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionName);
        ApiFuture<DocumentSnapshot> apiFuture = collection.document(document_id).get();
        DocumentSnapshot documentSnapshot = apiFuture.get();

        if(documentSnapshot.exists()){
            return documentSnapshot.toObject(entityClass);
        }

        return null;
    }

    // 특정 사용자 ID로 모든 Entity 검색 메서드
    @Override
    public List<T> findEntityAll(String user_id) throws ExecutionException, InterruptedException{
        CollectionReference collection = firestore.collection(collectionName);
        ApiFuture<QuerySnapshot> apiFutureList = collection
                .whereEqualTo("user_id", user_id)
                .get();
        QuerySnapshot querySnapshot = apiFutureList.get();

        if(querySnapshot != null){
            return querySnapshot.toObjects(entityClass);
        }

        return null;
    }

    /**
     * 여러가지 조건에 해당하는 단일 문서를 찾기 위한 메서드
     * 주로 특정 user_id에 특정 데이터를 가지는 문서를 찾기 위해 쓰임
     * @param fields Map형태로 Map.of()로 만들어 보내는 것이 편하다.
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Override
    public T findByFields(Map<String, Object> fields) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionName);
        Query query = buildQuery(collection, fields);
        ApiFuture<QuerySnapshot> apiFuture = query.get();
        QuerySnapshot querySnapshot = apiFuture.get();

        if(!querySnapshot.isEmpty()){
            return querySnapshot.getDocuments().get(0).toObject(entityClass);
        }
        return null;
    }

    /**
     * 여러 조건에 해당하는 여러개의 문서를 가져오기 위한 메서드
     * 예를 들어 특정 user_id에 특정 날짜에 해당하는 문서리스트
     * @param fields Map형태로 Map.of()로 만들어 보내는 것이 편하다.
     * @return
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Override
    public List<T> findAllByFields(Map<String, Object> fields) throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionName);
        Query query = buildQuery(collection, fields);
        ApiFuture<QuerySnapshot> apiFuture = query.get();
        QuerySnapshot querySnapshot = apiFuture.get();

        if (!querySnapshot.isEmpty()) {
            return querySnapshot.toObjects(entityClass);
        }
        return Collections.emptyList();
    }

    /**
     * entity 객체에서 문서 ID를 추출한다.
     * entity가 DocumentId를 포함하는 필드가 있다면 해당 필드에서 ID를 가져온다.
     * @param entity 추출하고 싶은 ID의 객체
     * @return ID값
     */
    private String getDocumentId(T entity) {
        try {
            Method getIdMethod = entity.getClass().getMethod("getId");
            return (String) getIdMethod.invoke(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get document ID", e);
        }
    }

    private Query buildQuery(CollectionReference collection, Map<String, Object> fields){
        Query query = collection;
        for(Map.Entry<String, Object> entry : fields.entrySet()){
            query = query.whereEqualTo(entry.getKey(), entry.getValue());
        }

        return query;
    }
}
