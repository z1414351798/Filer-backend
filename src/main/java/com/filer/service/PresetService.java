package com.filer.service;

import com.filer.mapper.PresetMapper;
import com.filer.mapper.UserMapper;
import com.filer.model.ConversionPreset;
import com.filer.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PresetService {

    private final PresetMapper presetMapper;
    private final UserMapper userMapper;

    public ConversionPreset create(String email, String name, String conversionType, String paramsJson) {
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ConversionPreset preset = new ConversionPreset();
        preset.setUserId(user.getId());
        preset.setName(name);
        preset.setConversionType(conversionType);
        preset.setParamsJson(paramsJson);
        presetMapper.insert(preset);
        return preset;
    }

    public List<ConversionPreset> list(String email) {
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return presetMapper.findByUserId(user.getId());
    }

    public void delete(Long id) {
        presetMapper.deleteById(id);
    }
}
