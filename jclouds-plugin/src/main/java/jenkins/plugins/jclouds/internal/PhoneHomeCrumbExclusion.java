package jenkins.plugins.jclouds.internal;

import hudson.Extension;
import hudson.security.csrf.CrumbExclusion;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.logging.Logger;

@Extension
public class PhoneHomeCrumbExclusion extends CrumbExclusion {

	private static final Logger LOGGER = Logger.getLogger("jenkins.plugins.jclouds.internal.PhoneHomeCrumbExclusion");

	@Override
	public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
		String pathInfo = req.getPathInfo();
		if (pathInfo != null && pathInfo.equals(getExclusionPath())) {
			chain.doFilter(req, resp);
			return true;
		}
		return false;
	}

	public String getExclusionPath() {
		return "/" + PhoneHomeWebHook.URLNAME + "/";
	}
}
