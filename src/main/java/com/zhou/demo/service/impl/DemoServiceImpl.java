package com.zhou.demo.service.impl;

import com.zhou.demo.service.IDemoService;
import com.zhou.mvcframework.annotation.ZService;


@ZService
public class DemoServiceImpl implements IDemoService {

	@Override
	public String get(String name) {
		return "My name is " + name;
	}

}
