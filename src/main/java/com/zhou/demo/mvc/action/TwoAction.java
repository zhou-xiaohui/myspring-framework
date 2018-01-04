package com.zhou.demo.mvc.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zhou.demo.service.impl.MyServiceImpl;
import com.zhou.mvcframework.annotation.ZAutowired;
import com.zhou.mvcframework.annotation.ZController;
import com.zhou.mvcframework.annotation.ZRequestMapping;
import com.zhou.mvcframework.annotation.ZRequestParam;

@ZController
@ZRequestMapping("two")
public class TwoAction {

	@ZAutowired("aa")
    MyServiceImpl myService;
	
	@ZRequestMapping("/edit.json")
	public void edit(HttpServletRequest request,HttpServletResponse response,
			@ZRequestParam("name") String name){
		String result = myService.get(name);
		try {
			response.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
