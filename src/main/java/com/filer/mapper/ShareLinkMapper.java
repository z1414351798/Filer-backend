package com.filer.mapper;

import com.filer.model.ShareLink;
import org.apache.ibatis.annotations.Mapper;
import java.util.Optional;

@Mapper
public interface ShareLinkMapper {
    void insert(ShareLink shareLink);
    Optional<ShareLink> findByToken(String token);
    void deleteExpired();
}
