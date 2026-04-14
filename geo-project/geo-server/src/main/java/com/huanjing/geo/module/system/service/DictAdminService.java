package com.huanjing.geo.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.dto.DictItemAdminVO;
import com.huanjing.geo.module.system.dto.DictItemCreateRequest;
import com.huanjing.geo.module.system.dto.DictItemUpdateRequest;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictAdminService {

    private final CurrentUserService currentUserService;
    private final SysDictItemMapper sysDictItemMapper;

    public Page<DictItemAdminVO> page(long current, long size, String dictType, String keyword, Boolean enabled) {
        currentUserService.ensurePermission("user.manage");

        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<SysDictItem>()
                .orderByAsc(SysDictItem::getDictType)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId);

        if (StringUtils.hasText(dictType)) {
            wrapper.eq(SysDictItem::getDictType, dictType.trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysDictItem::getDictKey, keyword.trim())
                    .or().like(SysDictItem::getDictValue, keyword.trim())
                    .or().like(SysDictItem::getRemark, keyword.trim()));
        }
        if (enabled != null) {
            wrapper.eq(SysDictItem::getEnabled, enabled);
        }

        Page<SysDictItem> page = sysDictItemMapper.selectPage(new Page<>(current, size), wrapper);
        Page<DictItemAdminVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toAdminVO).collect(Collectors.toList()));
        return result;
    }

    public List<String> dictTypes() {
        currentUserService.ensurePermission("user.manage");
        QueryWrapper<SysDictItem> wrapper = new QueryWrapper<SysDictItem>()
                .select("distinct dict_type")
                .orderByAsc("dict_type");
        return sysDictItemMapper.selectObjs(wrapper)
                .stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    @Transactional
    public DictItemAdminVO create(DictItemCreateRequest req) {
        currentUserService.ensurePermission("user.manage");
        ensureUnique(null, req.getDictType(), req.getDictKey());

        SysDictItem item = new SysDictItem();
        item.setDictType(req.getDictType().trim());
        item.setDictKey(req.getDictKey().trim());
        item.setDictValue(req.getDictValue().trim());
        item.setSortOrder(req.getSortOrder());
        item.setEnabled(req.getEnabled() == null || req.getEnabled());
        item.setRemark(normalizeRemark(req.getRemark()));
        sysDictItemMapper.insert(item);
        return toAdminVO(requireItem(item.getId()));
    }

    @Transactional
    public DictItemAdminVO update(Long id, DictItemUpdateRequest req) {
        currentUserService.ensurePermission("user.manage");
        SysDictItem item = requireItem(id);
        ensureUnique(id, req.getDictType(), req.getDictKey());

        item.setDictType(req.getDictType().trim());
        item.setDictKey(req.getDictKey().trim());
        item.setDictValue(req.getDictValue().trim());
        item.setSortOrder(req.getSortOrder());
        item.setRemark(normalizeRemark(req.getRemark()));
        sysDictItemMapper.updateById(item);
        return toAdminVO(requireItem(id));
    }

    @Transactional
    public void updateStatus(Long id, boolean enabled) {
        currentUserService.ensurePermission("user.manage");
        SysDictItem item = requireItem(id);
        item.setEnabled(enabled);
        sysDictItemMapper.updateById(item);
    }

    private SysDictItem requireItem(Long id) {
        SysDictItem item = sysDictItemMapper.selectById(id);
        if (item == null) {
            throw new BizException(404, "Dictionary item not found");
        }
        return item;
    }

    private void ensureUnique(Long currentId, String dictType, String dictKey) {
        SysDictItem existed = sysDictItemMapper.selectOne(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, dictType.trim())
                        .eq(SysDictItem::getDictKey, dictKey.trim())
                        .last("limit 1")
        );
        if (existed != null && !Objects.equals(existed.getId(), currentId)) {
            throw new BizException(400, "Dictionary type/key already exists");
        }
    }

    private DictItemAdminVO toAdminVO(SysDictItem item) {
        DictItemAdminVO vo = new DictItemAdminVO();
        vo.setId(item.getId());
        vo.setDictType(item.getDictType());
        vo.setDictKey(item.getDictKey());
        vo.setDictValue(item.getDictValue());
        vo.setSortOrder(item.getSortOrder());
        vo.setEnabled(item.getEnabled());
        vo.setRemark(item.getRemark());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        return remark.trim();
    }
}

