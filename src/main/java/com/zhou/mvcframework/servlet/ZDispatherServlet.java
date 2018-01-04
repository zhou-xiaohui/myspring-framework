package com.zhou.mvcframework.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.zhou.mvcframework.annotation.ZAutowired;
import com.zhou.mvcframework.annotation.ZController;
import com.zhou.mvcframework.annotation.ZRequestMapping;
import com.zhou.mvcframework.annotation.ZRequestParam;
import com.zhou.mvcframework.annotation.ZService;

public class ZDispatherServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private Properties p = new Properties();
	private List<String> classNames = new ArrayList<String>();
	private Map<String, Object> ioc = new HashMap<String, Object>();
	// private Map<String, Method> handlerMapping = new HashMap<String,
	// Method>();
	private List<Handle> handlerMapping = new ArrayList<ZDispatherServlet.Handle>();

	/**
	 * 初始化<br>
	 * 1、加载配置文件application.properties 代替xml <br>
	 * 2、扫描所有相关的类 , ---拿到基础包路径，递归扫描 <br>
	 * 3、把扫描到的类实例化放到IOC容器中去 (我们自己要写一个IOC容器 , 实际上是一个Map)<br>
	 * 4、依赖注入，只要加了@ZAutowired注解的字段，不管它是私有的还是公有的，还是受保护的，我们都要给它强制赋值<br>
	 * 5、获取用户的请求，根据所请求的url找到其对应的method，通过反射机制去调用<br>
	 * ---HandlerMapping 把这样一个关系存放到HandlerMapping，实际上是一个Map<br>
	 * 6、等待请求，把反射调用结果通过response写入到浏览器中
	 */
	@Override
	public void init(ServletConfig config) throws ServletException {
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		doScanner(p.getProperty("scanPackage"));
		doInstance();
		doAutowired();
		initHandlerMapping();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		try {
			doDispatch(req, resp);
		} catch (Exception e) {
			resp.getWriter().write(
					"500 Exception,Detail:"
							+ Arrays.toString(e.getStackTrace()));
		}
	}

	/**
	 * 修改前
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	// private void doDispatch(HttpServletRequest req, HttpServletResponse resp)
	// throws IOException {
	// String url = req.getRequestURI();
	// String contextPath = req.getContextPath();
	// url = url.replace(contextPath, "").replaceAll("/+", "/");
	// System.out.println(url);
	//
	// if (!handlerMapping.containsKey(url)) {
	// resp.getWriter().write("404 Not Found");
	// return;
	// }
	// Method method = handlerMapping.get(url);
	//
	// // 有个坑---invoke中
	// // 第一个参数为： 表示这个方法所在的实例
	// // method.invoke(obj, args);
	//
	// System.out.println("成功获取即将要调用的Method： " + method);
	// }

	/**
	 * 修改后
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		System.out.println(url);

		Handle handle = getHandle(req);
		if (handle == null) {
			// 如果没有匹配上，返回404错误
			resp.getWriter().write("404 Not Found");
			return;
		}

		// 获取方法的参数列表
		Class<?>[] paramTypes = handle.method.getParameterTypes();

		// 保存所有需要自动赋值的参数值
		Object[] paramValues = new Object[paramTypes.length];

		// 首先通过活动request的参数列表
		// 获得自己定义的方法的参数
		Map<String, String[]> params = req.getParameterMap();
		for (Entry<String, String[]> param : params.entrySet()) {
			System.out.println(Arrays.toString(param.getValue()));
			//String value = Arrays.toString(param.getValue()).replaceAll("\\[\\]", "").replaceAll(",\\s", ",");
			String value = Arrays.toString(param.getValue()).replaceAll("\\[", "").replaceAll("\\]", "");

			// 如果找到匹配的对象，则开始填充参数值
			if (!handle.paramIndexMapping.containsKey(param.getKey())) {
				continue;
			}
			int index = handle.paramIndexMapping.get(param.getKey());
			paramValues[index] = convert(paramTypes[index], value);
		}

		// 设置方法中的request和response对象
		int reqIndex = handle.paramIndexMapping.get(HttpServletRequest.class.getName());
		paramValues[reqIndex] = req;
		int respIndex = handle.paramIndexMapping.get(HttpServletResponse.class.getName());
		paramValues[respIndex] = resp;

		handle.method.invoke(handle.controller, paramValues);

		// System.out.println("成功获取即将要调用的Method： " + method);
	}

	private Handle getHandle(HttpServletRequest req) {
		if (handlerMapping.isEmpty()) {
			return null;
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");

		for (Handle handle : handlerMapping) {
			try {
				Matcher matcher = handle.pattern.matcher(url);
				// 如果没有匹配上继续下一个匹配
				if (!matcher.matches()) {
					continue;
				}
				return handle;
			} catch (Exception e) {
				throw e;
			}
		}
		return null;
	}

	private void doLoadConfig(String path) {
		InputStream is = this.getClass().getClassLoader()
				.getResourceAsStream(path);
		try {
			p.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void doScanner(String packageName) {
		URL url = this.getClass().getClassLoader()
				.getResource("/" + packageName.replaceAll("\\.", "/"));
		File dir = new File(url.getFile());

		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				doScanner(packageName + "." + file.getName());
			} else {
				// target/classes目录下不止有.class文件，也有.java文件
				if (file.getName().endsWith(".class")) {
					classNames.add(packageName + "."
							+ file.getName().replace(".class", ""));
				}
			}
		}
	}

	private void doInstance() {
		if (classNames.isEmpty()) {
			return;
		}

		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);

				// 不是所有的类都需要初始化的
				if (clazz.isAnnotationPresent(ZController.class)) {

					// <bean id="" name="" class="">
					String beanName = lowerFirst(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());

				} else if (clazz.isAnnotationPresent(ZService.class)) {
					/**
					 * 1、如果自己起了名字，优先使用自己的名字进行装配并注入<br>
					 * 2、默认首字母小写（发生在不是接口的情况）<br>
					 * 3、如果注入类型是接口，则要自动找到其实现类的实例并注入
					 */

					ZService service = clazz.getAnnotation(ZService.class);
					String beanName = service.value();// 如果设置了值，不等于""
					if (!"".equals(beanName.trim())) {
						ioc.put(beanName, clazz.newInstance());
					} else {
						beanName = lowerFirst(clazz.getSimpleName());
						ioc.put(beanName, clazz.newInstance());
					}

					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						ioc.put(i.getName(), clazz.newInstance());
					}

				} else {
					continue;
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void doAutowired() {

		if (ioc.isEmpty()) {
			return;
		}

		for (Entry<String, Object> entry : ioc.entrySet()) {
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if (!field.isAnnotationPresent(ZAutowired.class)) {
					continue;
				}

				// 如果注解加了自定义的名字
				ZAutowired autowired = field.getAnnotation(ZAutowired.class);

				String beanName = autowired.value().trim();

				// 通过声明接口注入
				if ("".equals(beanName.trim())) {
					beanName = field.getType().getName();
				}

				// 不管你愿不愿意，只要加了ZAutowired注解的，我们来实行强吻
				field.setAccessible(true);

				try {
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * private void initHandlerMapping() {
	 * 
	 * if (ioc.isEmpty()) { return; }
	 * 
	 * for (Entry<String, Object> entry : ioc.entrySet()) { // 非常具有技术含量
	 * 
	 * // 把所有的RequestMapping扫描出来，读取它的值，跟Method关联上，并且放入到handleMapping之中去。
	 * 
	 * Class<?> clazz = entry.getValue().getClass();
	 * 
	 * // 只跟ZController if (!clazz.isAnnotationPresent(ZController.class)) {
	 * continue; }
	 * 
	 * String baseUrl = ""; if
	 * (clazz.isAnnotationPresent(ZRequestMapping.class)) { ZRequestMapping
	 * requestMapping = clazz .getAnnotation(ZRequestMapping.class); baseUrl =
	 * requestMapping.value(); }
	 * 
	 * Method[] methods = clazz.getMethods();
	 * 
	 * for (Method method : methods) { if
	 * (!method.isAnnotationPresent(ZRequestMapping.class)) { continue; }
	 * 
	 * ZRequestMapping requestMapping = method
	 * .getAnnotation(ZRequestMapping.class); String mappingUrl = ("/" + baseUrl
	 * + requestMapping.value() .replaceAll("/+", "/"));
	 * 
	 * handlerMapping.put(mappingUrl, method);
	 * 
	 * System.out.println("Mapping: " + mappingUrl + ",Method: " + method); } }
	 * }
	 */

	private void initHandlerMapping() {

		if (ioc.isEmpty()) {
			return;
		}

		for (Entry<String, Object> entry : ioc.entrySet()) {
			// 非常具有技术含量

			// 把所有的RequestMapping扫描出来，读取它的值，跟Method关联上，并且放入到handleMapping之中去。

			Class<?> clazz = entry.getValue().getClass();

			// 只跟ZController
			if (!clazz.isAnnotationPresent(ZController.class)) {
				continue;
			}

			String baseUrl = "";
			if (clazz.isAnnotationPresent(ZRequestMapping.class)) {
				ZRequestMapping requestMapping = clazz
						.getAnnotation(ZRequestMapping.class);
				baseUrl = requestMapping.value();
			}

			Method[] methods = clazz.getMethods();

			for (Method method : methods) {
				if (!method.isAnnotationPresent(ZRequestMapping.class)) {
					continue;
				}

				ZRequestMapping requestMapping = method
						.getAnnotation(ZRequestMapping.class);
				String mappingUrl = ("/" + baseUrl + requestMapping.value()
						.replaceAll("/+", "/"));

				// 把url和Method的关系再重新封装一次
				Pattern pattern = Pattern.compile(mappingUrl);
				handlerMapping
						.add(new Handle(entry.getValue(), method, pattern));
				System.out.println("mapping " + mappingUrl + "," + method);
			}
		}
	}

	/**
	 * Handle记录Controller中的RequestMapping和Method的对应关系
	 */
	private class Handle {
		protected Object controller; // 保存方法对应的实例
		protected Method method; // 保存映射的方法
		protected Pattern pattern; // 记得Spring的url是支持正则的
		protected Map<String, Integer> paramIndexMapping; // 参数顺序

		protected Handle(Object controller, Method method, Pattern pattern) {
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;

			paramIndexMapping = new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}

		private void putParamIndexMapping(Method method) {
			// 提取方法中的接了注解的参数
			Annotation[][] pa = method.getParameterAnnotations();
			for (int i = 0; i < pa.length; i++) {
				for (Annotation a : pa[i]) {
					if (a instanceof ZRequestParam) {
						String paramName = ((ZRequestParam) a).value();
						if (!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}

			// 提取方法中的request和response参数
			Class<?>[] paramsTypes = method.getParameterTypes();
			for (int i = 0; i < paramsTypes.length; i++) {
				Class<?> type = paramsTypes[i];
				if (type == HttpServletRequest.class
						|| type == HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
	}

	private Object convert(Class<?> type, String value) {
		if (Integer.class == type) {
			return Integer.valueOf(value);
		}
		return value;
	}

	/**
	 * 首字母小写
	 * 
	 * @param str
	 * @return
	 */
	public String lowerFirst(String str) {
		char[] chars = str.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

}
