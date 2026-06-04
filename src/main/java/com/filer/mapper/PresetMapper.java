package com.filer.mapper;

import com.filer.model.ConversionPreset;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Optional;

@Mapper
public interface PresetMapper {
    void insert(ConversionPreset preset);
    List<ConversionPreset> findByUserId(Long userId);
    Optional<ConversionPreset> findById(Long id);
    void deleteById(Long id);
}
