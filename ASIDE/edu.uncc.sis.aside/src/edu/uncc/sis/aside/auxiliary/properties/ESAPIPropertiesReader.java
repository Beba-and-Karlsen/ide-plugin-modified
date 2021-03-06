package edu.uncc.sis.aside.auxiliary.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import edu.uncc.sis.aside.AsidePlugin;

public class ESAPIPropertiesReader {
	/*
	 * Absolute path to the user.home. No longer includes the ESAPI portion as
	 * it used to.
	 */
	private static final String userHome = System.getProperty("user.home");
	/*
	 * Absolute path to the customDirectory
	 */// DISCUSS: Implicit assumption here that there is no SecurityManager
		// installed enforcing the
		// prevention of reading system properties. Otherwise this will fail
		// with
		// SecurityException.
		// private final static String ESAPI_RESOURCES_ENTRY =
		// "org.owasp.esapi.resources";
	// private static String customDirectory =
	// System.getProperty(ESAPI_RESOURCES_ENTRY);
	private String customDirectory = null;
	/*
	 * Relative path to the resourceDirectory. Relative to the classpath.
	 * Specifically, ClassLoader.getResource(resourceDirectory + filename) will
	 * be used to load the file.
	 */
	private String resourceDirectory = ".esapi"; // For backward compatibility
	// (vs. "esapi")
	 //modified Mar. 3rd
	private String compatible_resourceDirectory =  "src" + IPath.SEPARATOR + "esapi";//"WebContent" + IPath.SEPARATOR + "WEB-INF" + IPath.SEPARATOR + "esapi"; 
   
	/** The name of the ESAPI property file */
	public static final String RESOURCE_FILE = "ESAPI.properties";

	public static final String VALIDATION_PROPERTIES = "Validator.ConfigurationFile";

	public static final String VALIDATOR_KEY_WORD = "Validator";

	private IProject fProject;
	private static URI locationUri;
	private static ESAPIPropertiesReader instance = null;

	private ESAPIPropertiesReader(IProject project) {
		fProject = project;
	}

	public static ESAPIPropertiesReader getInstance(IProject project) {

		if (instance == null) {
			synchronized (ESAPIPropertiesReader.class) {
				if (instance == null)
					instance = new ESAPIPropertiesReader(project);
			}
		}
		return instance;
	}

	public ArrayList<String> retrieveESAPIDefinedInputTypes() {
		ArrayList<String> definedInputTypes = new ArrayList<String>();
       
		IFolder folder = fProject.getFolder(compatible_resourceDirectory);

		if (folder.exists()) {
			locationUri = folder.getRawLocationURI();
		} else {
			folder = fProject.getFolder(resourceDirectory);
			if (folder.exists()) {
				locationUri = folder.getRawLocationURI();
			}
		}
		
		if (locationUri == null) {

			Shell shell = AsidePlugin.getDefault().getShell();
			MessageBox messageBox = new MessageBox(shell, SWT.OK
					| SWT.ICON_WARNING);
			messageBox.setText("FYI:");
			messageBox
					.setMessage("ASIDE cannot locate [esapi] folder under the current project directory!"
							+ "\n\nTo get the folder and required ESAPI configuration files, you may run ASIDE on the project manually by right clicking on the project name and navigating to ASIDE in the context menu, and selecting Run ASIDE"
							+ "\n\nMore help will be provided in next version of ASIDE...\nThus, you may need to figure out the solution this time.");
			messageBox.open();
			
			return definedInputTypes;
		}

		String path = locationUri.getPath();

		String os = System.getProperty("os.name");
		if (os.startsWith("Windows")) {
			customDirectory = path.substring(1);
		} else {
			customDirectory = path;
		}

		Properties properties = null;
		try {
			// first attempt file IO loading of properties
			InputStream is = getResourceStream(RESOURCE_FILE);
			properties = loadPropertiesFromStream(is);

		} catch (Exception iae) {
			iae.printStackTrace();
			// if file I/O loading fails, attempt classpath based loading next
			try {
				properties = loadConfigurationFromClasspath(RESOURCE_FILE);
			} catch (Exception e) {
				iae.printStackTrace();
			}
		}

		if (properties != null) {

			Set<Object> keys = properties.keySet();

			for (Object key : keys) {
				String sKey = (String) key;

				int dotIndex = sKey.indexOf(".");

				if (dotIndex != -1) {
					String subKey = sKey.substring(0, dotIndex);
					if (subKey.equals(VALIDATOR_KEY_WORD)) {
						String inputType = sKey.substring(dotIndex + 1);
						definedInputTypes.add(inputType);
					}
				}

			}

			String validationPropFileName = getESAPIProperty(properties,
					VALIDATION_PROPERTIES, "validation.properties");
			Properties validationProperties = null;

			try {
				// first attempt file IO loading of properties
				InputStream validation_is = getResourceStream(validationPropFileName);
				validationProperties = loadPropertiesFromStream(validation_is);

			} catch (Exception iae) {
				// if file I/O loading fails, attempt classpath based loading
				// next

				try {
					validationProperties = loadConfigurationFromClasspath(validationPropFileName);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			if (validationProperties != null) {
				Set<Object> keySet = validationProperties.keySet();
				for (Object key : keySet) {
					String sKey = (String) key;

					int dotIndex = sKey.indexOf(".");

					if (dotIndex != -1) {
						String subKey = sKey.substring(0, dotIndex);
						if (subKey.equals(VALIDATOR_KEY_WORD)) {
							String inputType = sKey.substring(dotIndex + 1);
							definedInputTypes.add(inputType);
						}
					}

				}
			}
		}

		return definedInputTypes;

	}

	protected String getESAPIProperty(Properties properties, String key,
			String def) {
		String value = properties.getProperty(key);
		if (value == null) {
			return def;
		}
		return value;
	}

	private Properties loadPropertiesFromStream(InputStream is)
			throws IOException {
		Properties config = new Properties();

		if (is == null)
			return config;

		try {
			config.load(is);
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		return config;
	}

	/**
	 * Used to load ESAPI.properties from a variety of different classpath
	 * locations.
	 * 
	 * @param fileName
	 *            The properties file filename.
	 */
	private Properties loadConfigurationFromClasspath(String fileName)
			throws IllegalArgumentException {
		Properties result = null;
		InputStream in = null;
		ClassLoader[] loaders = new ClassLoader[] {
				Thread.currentThread().getContextClassLoader(),
				ClassLoader.getSystemClassLoader(), getClass().getClassLoader() };

		ClassLoader currentLoader = null;
		for (int i = 0; i < loaders.length; i++) {
			if (loaders[i] != null) {
				currentLoader = loaders[i];
				try {
					// try root
					String currentClasspathSearchLocation = "/root"
							+ File.separator;
					in = loaders[i]
							.getResourceAsStream(currentClasspathSearchLocation
									+ fileName);

					// try resourceDirectory folder
					if (in == null) {
						currentClasspathSearchLocation = resourceDirectory
								+ File.separator;
						in = currentLoader
								.getResourceAsStream(currentClasspathSearchLocation
										+ fileName);
					}

					// try .esapi folder
					if (in == null) {
						currentClasspathSearchLocation = resourceDirectory
								+ File.separator;
						in = currentLoader
								.getResourceAsStream(currentClasspathSearchLocation
										+ fileName);
					}

					// try esapi folder (new directory)
					if (in == null) {
						currentClasspathSearchLocation = compatible_resourceDirectory
								+ File.separator;
						in = currentLoader
								.getResourceAsStream(currentClasspathSearchLocation
										+ fileName);
					}

					// try resources folder
					if (in == null) {
						currentClasspathSearchLocation = "resources"
								+ File.separator;
						in = currentLoader
								.getResourceAsStream(currentClasspathSearchLocation
										+ fileName);
					}

					// now load the properties
					if (in != null) {
						result = new Properties();
						result.load(in); // Can throw IOException
					}
				} catch (Exception e) {
					result = null;
					e.printStackTrace();

				} finally {
					if (in != null) {
						try {
							in.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

				}
			}
		}

		return result;
	}

	private InputStream getResourceStream(String filename) throws IOException {
		if (filename == null) {
			return null;
		}

		try {
			File f = getResourceFile(filename);
			if (f != null && f.exists()) {
				return new FileInputStream(f);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		Shell shell = AsidePlugin.getDefault().getShell();
		MessageBox messageBox = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
		messageBox.setText("FYI:");
		messageBox
				.setMessage("Cannot find File: "
						+ filename
						+ "!\n\nMake sure that ESAPI.properties file is under the current project directory!"
						+ "\n\nMore help will be provided in next version of ASIDE...\nThus, you may need to figure out the solution this time.");
		messageBox.open();
		return null;

	}

	private File getResourceFile(String filename) throws IOException {

		if (filename == null) {
			return null; // not found.
		}

		File f = null;

		// TODO first, allow command line overrides. -Dorg.owasp.esapi.resources
		// directory
		f = new File(customDirectory, filename);
		if (f != null && f.exists() && f.canRead()) {
			return f;
		}

		// if not found, then try the programmatically set resource directory
		// (this defaults to SystemResource directory/RESOURCE_FILE
		URL fileUrl = ClassLoader.getSystemResource(resourceDirectory
				+ File.separator + filename);
		if (fileUrl == null) {
			fileUrl = ClassLoader
					.getSystemResource(compatible_resourceDirectory
							+ File.separator + filename);
		}

		if (fileUrl != null) {
			String fileLocation = fileUrl.getFile();
			f = new File(fileLocation);
			if (f.exists() && f.canRead()) {
				return f;
			}
		}

		// If not found, then try immediately under user's home directory first
		// in
		// userHome + "/.esapi" and secondly under
		// userHome + "/esapi"
		// We look in that order because of backward compatibility issues.
		String homeDir = userHome;

		if (homeDir == null) {
			homeDir = ""; // Without this, homeDir + "/.esapi" would produce
			// the string "null/.esapi" which surely is not intended.
		}

		// First look under ".esapi" (for reasons of backward compatibility).
		String fileDir = homeDir + File.separator + resourceDirectory;
		f = new File(fileDir, filename);

		if (f.exists() && f.canRead()) {
			return f;
		} else {
			// Didn't find it under old directory ".esapi" so now look under the
			// "esapi" directory.
			fileDir = homeDir + File.separator + compatible_resourceDirectory;
			f = new File(fileDir, filename);
			if (f.exists() && f.canRead()) {
				return f;
			}

		}
		// return null if not found
		return null;
	}

}