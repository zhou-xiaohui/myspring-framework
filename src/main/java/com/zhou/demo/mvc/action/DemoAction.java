package com.zhou.demo.mvc.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zhou.demo.service.IDemoService;
import com.zhou.mvcframework.annotation.ZAutowired;
import com.zhou.mvcframework.annotation.ZController;
import com.zhou.mvcframework.annotation.ZRequestMapping;
import com.zhou.mvcframework.annotation.ZRequestParam;

@ZController
@ZRequestMapping("web")
public class DemoAction {

	@ZAutowired
	IDemoService demoService;
	
	@ZRequestMapping("/query.json")
	public void query(HttpServletRequest request,HttpServletResponse response,
			@ZRequestParam("name") String name){
		String result = demoService.get(name);
		try {
			response.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@ZRequestMapping("/add.json")
	public void add(HttpServletRequest request,HttpServletResponse response){
	}
	
	@ZRequestMapping("/update.json")
	public void update(HttpServletRequest request,HttpServletResponse response){
	}
	
	@ZRequestMapping("/delete.json")
	public void delete(HttpServletRequest request,HttpServletResponse response){
	}
}
