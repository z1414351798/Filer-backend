package com.filer.mapper;
import com.filer.model.ApiKey;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface ApiKeyMapper {
    void insert(ApiKey key);
    List<ApiKey> findByUserId(Long userId);
    ApiKey findByKeyValue(String keyValue);
    void deactivate(@Param("id") Long id, @Param("userId") Long userId);
    void updateLastUsed(String keyValue);
}
