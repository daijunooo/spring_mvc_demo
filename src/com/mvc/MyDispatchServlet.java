package com.mvc;

import com.mvc.framework.annotation.Autowired;
import com.mvc.framework.annotation.Controller;
import com.mvc.framework.annotation.RequestMapping;
import com.mvc.framework.annotation.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatchServlet extends HttpServlet {

    //保存所有扫描到的相关类
    public List<String> classes = new ArrayList<>();

    //保存所有初始化的bean
    public Map<String, Object> ioc = new HashMap<>();

    //保存所有url和method的映射关系
    public Map<String, Method> urls = new HashMap<>();

    //和web.xml里init-param的值一致
    private static final String LOCATION = "contextConfigLocation";

    //保存配置的所有信息
    private Properties config = new Properties();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (urls.isEmpty()) return;
        String contextPath = req.getContextPath();
        String url = req.getRequestURI().replace(contextPath, "").replaceAll("/+", "/");
        if (!urls.containsKey(url)) {
            resp.getWriter().write("...404");
            return;
        }
        Method method = urls.get(url);
        String beanName = method.getDeclaringClass().getName();
        String result = (String) method.invoke(ioc.get(beanName));
        resp.getWriter().write(result);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("----------------------------框架初始化开始---------------------------");

        super.init(config);

        //加载配置文件
        loadConfig(config.getInitParameter(LOCATION));

        //扫描所有相关类
        scanner(this.config.getProperty("scanPackage"));

        //初始化所有相关类的实例保存到IOC容器中(Controller)
        initIoc();

        //依赖注入(Service)
        initDi();

        //生成url映射(RequestMapping)
        initUrl();

        System.out.println("----------------------------框架初始化结束---------------------------");
    }


    private void loadConfig(String location) {
        InputStream i = getServletContext().getResourceAsStream(location);
        try {
            config.load(i);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                i.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }


    private void scanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                scanner(packageName + "." + file.getName());
            } else {
                classes.add(packageName + "." + file.getName().replace(".class", "").trim());
            }
        }
    }


    private void initIoc() {
        if (classes.size() == 0) return;
        for (String className : classes) {
            try {
                Class<?> classObject = Class.forName(className);
                if (classObject.isAnnotationPresent(Controller.class)) {
                    ioc.put(className, classObject.newInstance());
                } else if (classObject.isAnnotationPresent(Service.class)) {
                    Service service = classObject.getAnnotation(Service.class);
                    String beanName = service.value().trim();
                    if (!beanName.equals("")) {
                        ioc.put(beanName, classObject.newInstance());
                        continue;
                    }
                    Class<?>[] interfaces = classObject.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), classObject.newInstance());
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void initDi() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(Autowired.class)) continue;
                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value();
                if (beanName.equals("")) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    
    private void initUrl() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> className = entry.getValue().getClass();
            if (!className.isAnnotationPresent(Controller.class)) continue;
            StringBuilder baseUrl = new StringBuilder("/");
            if (className.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = className.getAnnotation(RequestMapping.class);
                baseUrl.append(requestMapping.value());
            }
            Method[] methods = className.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) continue;
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String url = baseUrl.append("/" + requestMapping.value()).toString().replaceAll("/+", "/");
                urls.put(url, method);
            }
        }
    }

}
