package com.hakimi.aviation.repository;

import com.hakimi.aviation.es.FlightIndexDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlightIndexRepository extends ElasticsearchRepository<FlightIndexDoc, Long> {
    // 基础的 save(Upsert), findById 等方法已经自带，无需手写
}
