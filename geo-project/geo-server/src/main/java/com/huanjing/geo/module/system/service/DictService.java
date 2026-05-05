package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.system.dto.DictItemVO;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictService {

    private final CurrentUserService currentUserService;
    private final SysDictItemMapper sysDictItemMapper;

    public Map<String, List<DictItemVO>> listByTypes(String types) {
        currentUserService.requireCurrentUser();
        List<String> typeList = parseTypes(types);

        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getEnabled, true)
                .orderByAsc(SysDictItem::getDictType, SysDictItem::getSortOrder, SysDictItem::getId);

        if (!typeList.isEmpty()) {
            wrapper.in(SysDictItem::getDictType, typeList);
        }

        List<SysDictItem> items = sysDictItemMapper.selectList(wrapper);
        Map<String, List<DictItemVO>> grouped = new LinkedHashMap<>();
        for (SysDictItem item : items) {
            grouped.computeIfAbsent(item.getDictType(), k -> new ArrayList<>()).add(toVO(item));
        }
        return grouped;
    }

    private DictItemVO toVO(SysDictItem item) {
        DictItemVO vo = new DictItemVO();
        vo.setDictType(item.getDictType());
        vo.setDictKey(item.getDictKey());
        vo.setDictValue(item.getDictValue());
        vo.setSortOrder(item.getSortOrder());
        vo.setRemark(item.getRemark());
        return vo;
    }

    private List<String> parseTypes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return List.of(raw.split(","))
                .stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
