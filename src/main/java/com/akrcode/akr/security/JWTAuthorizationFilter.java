package com.akrcode.akr.security;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWTAuthorizationFilter Class This Class defines---- Validate the token
 * 
 * @author akash
 *
 */

public class JWTAuthorizationFilter extends OncePerRequestFilter {

	private final String HEADER = "Authorization";
	private final String PREFIX = "Bearer ";
	private final String SECRET = "Lenter@CXP&oR3Ec7M5XwE0Ypzc1zSg9IT4Xe1QbIia3Kn3XfS4FqA8kq7HRLsxuYuPoDwZJXz7tzx8sa1HBAnG2k8IhBaxTNe5lphI61ZtiwHUeoXqOAD5eobJdYVKgjmcfDG8SkSA8pbjYq46hjj48WJ42YbqlRhSANX99OAKIutJOTONPW9iTBrgSrYXgL8bGBKbAHITYpluMwMu";
	public String auth = "";
	public String role = "";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE,PUT");
		response.setHeader("Access-Control-Allow-Headers", "*");
		response.setHeader("Access-Control-Max-Age", "3600");
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			response.setStatus(HttpServletResponse.SC_OK);
		} else {
			try {
				if (checkJWTToken(request, response)) {
					Claims claims = validateToken(request);
					if (claims.get("authorities") != null) {
						setUpSpringAuthentication(claims);
					} else {
						SecurityContextHolder.clearContext();
					}
				} else {
					SecurityContextHolder.clearContext();
				}
				chain.doFilter(request, response);
			} catch (ExpiredJwtException e) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				Map<String, Object> responseBody = new HashMap<>();
				responseBody.put("result", false);
				responseBody.put("message", "Token expired");
				response.getWriter().write(new ObjectMapper().writeValueAsString(responseBody));

			} catch (UnsupportedJwtException | MalformedJwtException e) {
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				Map<String, Object> responseBody = new HashMap<>();
				responseBody.put("result", false);
				responseBody.put("message", "Invalid token");
				response.getWriter().write(new ObjectMapper().writeValueAsString(responseBody));
			}
		}
	}

	private Claims validateToken(HttpServletRequest request) {
		String jwtToken = request.getHeader(HEADER).replace(PREFIX, "");
		return Jwts.parser().setSigningKey(SECRET.getBytes()).parseClaimsJws(jwtToken).getBody();
	}

	/**
	 * Authentication method in Spring flow
	 * 
	 * @param claims
	 */
	private void setUpSpringAuthentication(Claims claims) {
		@SuppressWarnings("unchecked")
		List<String> authorities = (List) claims.get("authorities");
		int orgid = claims.get("orgid", Integer.class);
		int userid = claims.get("userid", Integer.class);
		int roleid = claims.get("roleid", Integer.class);
		String databasename = claims.get("databasename", String.class);
		String timezone = claims.get("timezone", String.class);

		AppConfig.session.put("roleid", roleid);
		AppConfig.session.put("orgId", orgid);
		AppConfig.session.put("timezone", timezone);
		AppConfig.session.put("userid", userid);
		AppConfig.session.put("databasename", databasename);

		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null,
				authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
		SecurityContextHolder.getContext().setAuthentication(auth);

	}

	private boolean checkJWTToken(HttpServletRequest request, HttpServletResponse res) {
		String authenticationHeader = request.getHeader(HEADER);
		if (authenticationHeader == null || !authenticationHeader.startsWith(PREFIX))
			return false;
		return true;
	}

}
