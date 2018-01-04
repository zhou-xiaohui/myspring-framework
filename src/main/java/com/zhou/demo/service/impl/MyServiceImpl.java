package com.zhou.demo.service.impl;

import com.zhou.demo.service.IDemoService;
import com.zhou.mvcframework.annotation.ZService;


@ZService("aa")
public class MyServiceImpl implements IDemoService {

	@Override
	public String get(String name) {
		return "My name is " + name;
	}

}
