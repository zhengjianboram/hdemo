package com.ram.kettle.util;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ram.kettle.cache.DataCacheApplication;
import com.ram.kettle.controller.CtrlDb;
import com.ram.kettle.controller.DataController;
import com.ram.kettle.controller.impl.KettleDataController;
import com.ram.kettle.database.DataSourceApplication;
import com.ram.kettle.database.DatabaseInterface;
import com.ram.kettle.database.DatabaseMeta;
import com.ram.kettle.database.KettleApplication;
import com.ram.kettle.log.BaseMessages;
import com.ram.kettle.log.ConstLog;
import com.ram.kettle.log.KettleException;
import com.ram.kettle.tran.SingleRowTransExecutor;
import com.ram.kettle.tran.Trans;
import com.ram.kettle.tran.TransMeta;
import com.ram.kettle.xml.KettleVFS;
import com.ram.kettle.xml.XMLHandler;
import com.ram.server.util.BaseLog;

public class KettleEnvironment {

	private static Class<?> PKG = Const.class;

	private static Map<String, String> pluginMap;
	private static Map<String, DatabaseInterface> allDatabaseInterfaces;

	private static Boolean initialized;

	public static String KTRDBPOOL = "";
	public static String CACHE = "";
	public static String KTRHOME = "";
	public static String SHARED = "shared.xml";

	private static final String XML_TAG = "sharedobjects";

	public static void init() throws Exception {
		init(false);
	}

	public static void init(String path) throws Exception {
		// 若未曾初始化
		if (initialized == null) {
			createKettleHome();
			EnvUtil.environmentInit();
			loadShareDataBase(path);
			loadCache();
			loadKtr(path + KTRHOME); 
			initialized = true;
		}
	}

	public static void init(boolean simpleJndi) throws Exception {
		// 若未曾初始化
		if (initialized == null) {
			try {
				createKettleHome();
				EnvUtil.environmentInit();
				if (simpleJndi) {
					JndiUtil.initJNDI();
				}
				loadCache();
				loadShareDataBase("");
				loadKtr(KTRHOME);
			} catch (Exception e) {
			}
			initialized = true;
		}
	}

	public static void loadKtr(String path) {
		File ktrFileFolder = new File(path);
		if (ktrFileFolder.exists() && ktrFileFolder.isDirectory()) {
			// 遍历
			File[] ktrFiles = ktrFileFolder.listFiles();
			for (File ktrFile : ktrFiles) {
				if (ktrFile.isFile()) {
					String fileName = ktrFile.getName();
					String suffix = fileName.substring(fileName
							.lastIndexOf(".") + 1);
					if (suffix.toLowerCase().indexOf("ktr") > -1) {
						try {
							add(ktrFile.getPath(), true);
						} catch (Exception e) {
							e.printStackTrace();
							BaseLog.debug(e.getMessage());
						}
					}
				} else {
					// 遍历节点
					loadKtr(ktrFile.getPath());
				}
			}
		}
	}

	public static void loadCache() {
		try {
			DataCacheApplication.getInstance(KettleEnvironment.CACHE);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final CtrlDb controllers = new CtrlDb();

	public static void add(String key, String filename, boolean rewrite)
			throws Exception {
		add(filename, rewrite);
	}

	public static void add(String filename, boolean rewrite) throws Exception {
		BaseLog.debug("init:"+filename);
		long starttime = System.currentTimeMillis();
		TransMeta transMeta = new TransMeta(filename);
		String key = transMeta.getName();
		key = key.toUpperCase();
		Object tranExecutor = KettleApplication.getInstanceSingle().get(key);
		if (tranExecutor == null || rewrite) {
			Trans trans = new Trans(transMeta);
			trans.prepareExcute();
			final SingleRowTransExecutor executor = new SingleRowTransExecutor(
					trans, KettleApplication.INPUT, KettleApplication.OUTPUT);
			executor.init();
			KettleApplication.getInstanceSingle().put(key, executor);
		}
		DataController controller = new KettleDataController();
		controllers.add(key, controller);
		ConstLog.message(filename + "--time:"
				+ (System.currentTimeMillis() - starttime));

	}
	public static TransMeta addTransMeta(String filename, boolean rewrite) throws Exception {
		BaseLog.debug("init:"+filename);
		long starttime = System.currentTimeMillis();
		TransMeta transMeta = new TransMeta(filename);
		String key = transMeta.getName();
		key = key.toUpperCase();
		Object tranExecutor = KettleApplication.getInstanceSingle().get(key);
		if (tranExecutor == null || rewrite) {
			Trans trans = new Trans(transMeta);
			trans.prepareExcute();
			final SingleRowTransExecutor executor = new SingleRowTransExecutor(
					trans, KettleApplication.INPUT, KettleApplication.OUTPUT);
			executor.init();
			KettleApplication.getInstanceSingle().put(key, executor);
		}
		DataController controller = new KettleDataController();
		controllers.add(key, controller);
		return transMeta;
	}
	private static DataSourceApplication instance = DataSourceApplication
			.getInstanceSingle();

	public static void loadShareDataBase(String path) throws KettleException,
			FileSystemException { 
		String kettlePropsFilename = path + "/" + SHARED; 
		
		FileObject file = KettleVFS.getFileObject(kettlePropsFilename);

		if (file.exists()) {
			Document document = XMLHandler.loadXMLFile(file);
			Node sharedObjectsNode = XMLHandler.getSubNode(document, XML_TAG);
			if (sharedObjectsNode != null) { 
				NodeList childNodes = sharedObjectsNode.getChildNodes(); 
				for (int i = 0; i < childNodes.getLength(); i++) {
					Node node = childNodes.item(i);
					String nodeName = node.getNodeName(); 
					if (nodeName.equals(DatabaseMeta.XML_TAG)) {
						DatabaseMeta sharedDatabaseMeta = new DatabaseMeta(node); 
						instance.putMeta(sharedDatabaseMeta.getName(),
								sharedDatabaseMeta); 
					}
				}

			}
		}
	}

	public static String getClassName(String typeid) throws KettleException {
		if (pluginMap == null) {
			throw new KettleException("STEP TYPE " + typeid + " LOAD ERROR!");
		}
		if (pluginMap.get(typeid) == null) {
			throw new KettleException("STEP TYPE " + typeid + " NOT FOUND:"
					+ typeid);
		}
		return pluginMap.get(typeid);
	}

	public static void createKettleHome() throws Exception {
		// 加载stepid 对应meta
		pluginMap = new HashMap<String, String>();
		getClasses();

		getDatabaseInterfacesMap();

	}

	// public static final
	public static Map<String, DatabaseInterface> getDatabaseInterfacesMap()
			throws Exception {
		if (allDatabaseInterfaces != null) {
			return allDatabaseInterfaces;
		}

		HashMap<String, DatabaseInterface> tmpAllDatabaseInterfaces = new HashMap<String, DatabaseInterface>();

		Class clazz = KettleEnvironment.class;
		ClassLoader loader = clazz.getClassLoader();
		// 1. 通过classloader载入包路径，得到url
		URL url = loader.getResource("com/ram/kettle/database/impl");
		URI uri = url.toURI();
		// 2. 通过File获得uri下的所有文件
		File file = new File(uri);
		File[] files = file.listFiles();
		for (File f : files) {
			String fName = f.getName();
			if (!fName.endsWith(".class")) {
				continue;
			}
			fName = fName.substring(0, fName.length() - 6);
			String perfix = "com.ram.kettle.database.impl.";
			String allName = perfix + fName;
			try {// 3. 通过反射加载类
				Class xclazz = Class.forName(allName);
				Object obj = xclazz.newInstance();

				DatabaseInterface databaseInterface = (DatabaseInterface) obj;
				tmpAllDatabaseInterfaces.put(databaseInterface.getPluginId()
						.toUpperCase(), databaseInterface);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		allDatabaseInterfaces = tmpAllDatabaseInterfaces;
		return allDatabaseInterfaces;
	}

	public static void getClasses() throws Exception {
		Class clazz = KettleEnvironment.class;
		ClassLoader loader = clazz.getClassLoader();
		// 1. 通过classloader载入包路径，得到url
		URL url = loader.getResource("com/ram/kettle/steps/impl");
		URI uri = url.toURI();
		// 2. 通过File获得uri下的所有文件
		File file = new File(uri);
		File[] files = file.listFiles();
		for (File f : files) {
			String fName = f.getName();
			if (!fName.endsWith(".class")) {
				continue;
			}
			if (fName.indexOf("$") > -1) {
				continue;
			}
			fName = fName.substring(0, fName.length() - 6);
			String perfix = "com.ram.kettle.steps.impl.";
			String allName = perfix + fName;
			// 3. 通过反射加载类
			clazz = Class.forName(allName);
			Object obj = clazz.newInstance();
			Method method = clazz.getMethod("getTypeId");
			Object object = method.invoke(obj);

			pluginMap.put(object + "", allName);

		}
	}

	public <T> T loadClass(String typeid, String className)
			throws KettleException {
		if (typeid == null) {
			throw new KettleException(
					BaseMessages
							.getString(PKG,
									"PluginRegistry.RuntimeError.NoValidStepOrPlugin.PLUGINREGISTRY001")); //$NON-NLS-1$
		}

		if (className == null) {
			throw new KettleException(
					BaseMessages
							.getString(
									PKG,
									"PluginRegistry.RuntimeError.NoValidClassRequested.PLUGINREGISTRY002", className)); //$NON-NLS-1$
		}

		try {
			@SuppressWarnings("unchecked")
			Class<? extends T> cl = (Class<? extends T>) Class
					.forName(className);
			return cl.newInstance();
		} catch (ClassNotFoundException e) {
			throw new KettleException(
					BaseMessages
							.getString(PKG,
									"PluginRegistry.RuntimeError.ClassNotFound.PLUGINREGISTRY003"), e); //$NON-NLS-1$
		} catch (InstantiationException e) {
			throw new KettleException(
					BaseMessages
							.getString(PKG,
									"PluginRegistry.RuntimeError.UnableToInstantiateClass.PLUGINREGISTRY004"), e); //$NON-NLS-1$
		} catch (IllegalAccessException e) {
			throw new KettleException(
					BaseMessages
							.getString(PKG,
									"PluginRegistry.RuntimeError.IllegalAccessToClass.PLUGINREGISTRY005"), e); //$NON-NLS-1$
		} catch (Throwable e) {
			e.printStackTrace();
			throw new KettleException(
					BaseMessages
							.getString(PKG,
									"PluginRegistry.RuntimeError.UnExpectedErrorLoadingClass.PLUGINREGISTRY007"), e); //$NON-NLS-1$
		}
	}

	public static boolean isInitialized() {
		if (initialized == null)
			return false;
		else
			return true;
	}

}
