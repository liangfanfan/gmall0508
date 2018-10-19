package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.BaseAttrInfo;

import java.util.List;

public interface AttrService {

    List<BaseAttrInfo> getAttrList(String catalog3Id);

    void saveAttrInfo(BaseAttrInfo attrInfo);

    BaseAttrInfo getAttrInfo(String id);

    void removeAttrInfo(String id);

    List<BaseAttrInfo> getAttrListByValueId(String join);
}
