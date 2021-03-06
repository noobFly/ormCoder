package cn.org.rapid_framework.generator.util.sqlparse;
import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.org.rapid_framework.generator.util.GLogger;
import cn.org.rapid_framework.generator.util.IOHelper;
import cn.org.rapid_framework.generator.util.StringHelper;


public class SqlParseHelper {

	public static class NameWithAlias {
		private String name;
		private String alias;
		public NameWithAlias(String tableName, String tableAlias) {
			if(tableName.trim().indexOf(' ') >= 0) throw new IllegalArgumentException("error name:"+tableName);
			if(tableAlias != null && tableAlias.trim().indexOf(' ') >= 0) throw new IllegalArgumentException("error alias:"+tableAlias);
			this.name = tableName.trim();
			this.alias = tableAlias == null ? null : tableAlias.trim();
		} 
		public String getName() {
			return name;
		}
		public String getAlias() {
			return StringHelper.isBlank(alias) ? getName() : alias;
		}
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((name == null) ? 0 : name.hashCode());
			return result;
		}
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			NameWithAlias other = (NameWithAlias) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
		public String toString() {
			return StringHelper.isBlank(alias) ? name  : name +" as " + alias;
		}
	}
	
	static Pattern from = Pattern.compile("(from\\s+)([,\\w]+)",
			Pattern.CASE_INSENSITIVE);
	static Pattern join = Pattern.compile("(join\\s+)(\\w+)(as)?(\\w*)",
			Pattern.CASE_INSENSITIVE);
	static Pattern update = Pattern.compile("(\\s*update\\s+)(\\w+)",
			Pattern.CASE_INSENSITIVE);
	static Pattern insert = Pattern.compile("(\\s*insert\\s+into\\s+)(\\w+)",
			Pattern.CASE_INSENSITIVE);
		
	public static Set<NameWithAlias> getTableNamesByQuery(String sql) {
		sql = sql.trim();
		Set<NameWithAlias> result = new LinkedHashSet();
		Matcher m = from.matcher(sql);
		if (m.find()) {
			String from = getFromClauses(sql);
			if(from.indexOf(',') >= 0) {
				//?????????????????????
				String[] array = StringHelper.tokenizeToStringArray(from, ",");
				for(String s : array) {
					result.add(parseSqlAlias(s));
				}
			}else if(from.indexOf("join") >= 0) {
				//join?????????
				String removedFrom = StringHelper.removeMany(from.toLowerCase(),"inner","full","left","right","outer");
				String[] joins = removedFrom.split("join");
				for(String s : joins) {
					result.add(parseSqlAlias(s));
				}
			}else {
				//??????
				result.add(parseSqlAlias(from));
			}
		}

		m = update.matcher(sql);
		if (m.find()) {
			result.add(new NameWithAlias(m.group(2),null));
		}

		m = insert.matcher(sql);
		if (m.find()) {
			result.add(new NameWithAlias(m.group(2),null));
		}
		return result;
	}
		
	/** ??????sql?????????,??? user as u,????????? user???u */
	public static NameWithAlias parseSqlAlias(String str) {
		String[] array = str.split("\\sas\\s");
		if(array.length >= 2) {
			return new NameWithAlias(array[0],StringHelper.tokenizeToStringArray(array[1], " \n\t")[0]);
		}
		array = StringHelper.tokenizeToStringArray(str, " \n\t");
		if(array.length >= 2) {
			return new NameWithAlias(array[0], array[1]);
		}
		return new NameWithAlias(str.trim(),null);
	}


//	static Pattern p = Pattern.compile("(:)(\\w+)(\\|?)([\\w.]+)");
	public static String getParameterClassName(String sql, String paramName) {
		Pattern p = Pattern.compile("(:)(" + paramName + ")(\\|?)([\\w.]+)");
		Matcher m = p.matcher(sql);
		if (m.find()) {
			return m.group(4);
		}
		return null;
	}
	
	public static String getColumnNameByRightCondition(String sql,String column) {
		String operator = "[=<>!]{1,2}";
		String result = getColumnNameByRightCondition(sql, column, operator);
		if(result == null) {
			result = getColumnNameByRightCondition(sql, column, "\\s+like\\s+");
		}
		if(result == null) {
			result = getColumnNameByRightCondition(sql, column, "\\s+between\\s+");
		}
		if(result == null) {
			result = getColumnNameByRightCondition(sql, column, "\\s+between\\s.+\\sand\\s+");
		}
		if(result == null) {
			result = getColumnNameByRightCondition(sql, column, "\\s+in\\s+\\(");
		}
		return result;
	}

	private static String getColumnNameByRightCondition(String sql,
			String column, String operator) {
		Pattern p = Pattern.compile("(\\w+)\\s*"+operator+"\\s*[:#$&]\\{?"+column+"[\\}#$]?",Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(sql);
		if(m.find()) {
			return m.group(1);
		}
		return null;
	}

	public static String convert2NamedParametersSql(String sql,String prefix,String suffix) {
	    return new NamedSqlConverter(prefix,suffix).convert2NamedParametersSql(sql);
	}

	 /**
     * ???sql?????????????????????????????????,??? select * from user where id =? ,?????????: select * from user where id = #id#
     * @param sql
     * @param prefix ?????????????????????
     * @param suffix ?????????????????????
     * @return
     */
	public static class NamedSqlConverter {
	    private String prefix;
	    private String suffix;
	    
        public NamedSqlConverter(String prefix, String suffix) {
            if(prefix == null) throw new NullPointerException("'prefix' must be not null");
            if(suffix == null) throw new NullPointerException("'suffix' must be not null");
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public String convert2NamedParametersSql(String sql) {
	        if(sql.trim().toLowerCase().matches("(?is)\\s*insert\\s+into\\s+.*")) {
	            return replace2NamedParameters(replaceInsertSql2NamedParameters(sql));
	        }else {
	            return replace2NamedParameters(sql);
	        }
	    }

        private String replace2NamedParameters(String sql) {
            String replacedSql = replace2NamedParametersByOperator(sql,"[=<>!]{1,2}");
            return replace2NamedParametersByOperator(replacedSql,"\\s+like\\s+");
        }

        private String replaceInsertSql2NamedParameters(String sql) {
        	if(sql.matches("(?is)\\s*insert\\s+into\\s+\\w+\\s+values\\s*\\(.*\\).*")) {
        		if(sql.indexOf("?") >= 0) {
        			throw new IllegalArgumentException("???????????????insert sql:"+sql+",values()?????????????????????????");
        		} else {
        			return sql;
        		}
        	}
            Pattern p = Pattern.compile("\\s*insert\\s+into.*\\((.*?)\\).*values.*?\\((.*)\\).*",Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(sql);
            if(m.find()) {
                String[] columns = StringHelper.tokenizeToStringArray(m.group(1),", \t\n\r\f");
                String[] values = StringHelper.tokenizeToStringArray(m.group(2),", \t\n\r\f");
                if(columns.length != values.length) {
                    throw new IllegalArgumentException("insert ??????????????????????????????????????????,sql:"+sql+" columns:"+StringHelper.join(columns,",")+" values:"+StringHelper.join(values,","));
                }
                for(int i = 0; i < columns.length; i++) {
                    String column = columns[i];
                    String paranName = StringHelper.uncapitalize(StringHelper.makeAllWordFirstLetterUpperCase(column));
                    values[i] = values[i].replace("?", prefix + paranName + suffix);;
                }
                return StringHelper.replace(m.start(2), m.end(2), sql, StringHelper.join(values,","));
            }
            throw new IllegalArgumentException("???????????????sql:"+sql+",????????????????????????:"+p.pattern());
        }
	    
        private String replace2NamedParametersByOperator(String sql,String operator) {
            Pattern p = Pattern.compile("(\\w+)\\s*"+operator+"\\s*\\?",Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(sql);
            StringBuffer sb = new StringBuffer();
            while(m.find()) {
                String segment = m.group(0);
                String columnSqlName = m.group(1);
                String paramName = StringHelper.uncapitalize(StringHelper.makeAllWordFirstLetterUpperCase(columnSqlName));
                String replacedSegment = segment.replace("?", prefix + paramName + suffix);
                m.appendReplacement(sb, replacedSegment);
            }
            m.appendTail(sb);
            return sb.toString();
        }
	}

	/**
	 * ??????sql
	 * 
	 * @param sql
	 * @return
	 */
	public static String getPrettySql(String sql) {
		try {
			if (IOHelper.readLines(new StringReader(sql)).size() > 1) {
				return sql;
			} else {
				return StringHelper.replace(StringHelper.replace(sql, "from",
						"\n\tfrom"), "where", "\n\twhere");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * ??????select ??????????????????union?????????
	 * 
	 * @param sql
	 * @return
	 */
	public static String removeSelect(String sql) {
		if(StringHelper.isBlank(sql)) throw new IllegalArgumentException("sql must be not empty");
		int beginPos = sql.toLowerCase().indexOf("from");
		if(beginPos == -1) throw new IllegalArgumentException(" sql : " + sql + " must has a keyword 'from'");
		return sql.substring(beginPos);
	}

	public static String getSelect(String sql) {
		if(StringHelper.isBlank(sql)) throw new IllegalArgumentException("sql must be not empty");
		int beginPos = sql.toLowerCase().indexOf("from");
		if(beginPos == -1) throw new IllegalArgumentException(" sql : " + sql + " must has a keyword 'from'");
		return sql.substring(0,beginPos);
	}
	
	/** ??????sql???from?????? */
	public static String getFromClauses(String sql) {
		String lowerSql = sql.toLowerCase();
		int fromBegin = lowerSql.indexOf("from");
		int fromEnd = lowerSql.indexOf("where");
		if(fromEnd == -1) {
			fromEnd = StringHelper.indexOfByRegex(lowerSql,"group\\s+by");
		}
		if(fromEnd == -1) {
			fromEnd = lowerSql.indexOf("having");
		}
		if(fromEnd == -1) {
			fromEnd = StringHelper.indexOfByRegex(lowerSql,"order\\s+by");
		}
		if(fromEnd == -1) {
			fromEnd = lowerSql.indexOf("union");
		}
		if(fromEnd == -1) {
			fromEnd = lowerSql.indexOf("intersect");
		}
		if(fromEnd == -1) {
			fromEnd = lowerSql.indexOf("minus");
		}
		if(fromEnd == -1) {
			fromEnd = lowerSql.indexOf("except");
		}
		if(fromEnd == -1) {
			fromEnd = sql.length();
		}
		return sql.substring(fromBegin+"from".length(),fromEnd);
	}

	
    /**
     * ??????orderby ??????
     * @param sql
     * @return
     */
    public static String removeOrders(String sql) {
    	return sql.replaceAll("(?is)order\\s+by[\\w|\\W|\\s|\\S]*", "");
    }
	
	public static long startTimes = System.currentTimeMillis();
	public static void setRandomParamsValueForPreparedStatement(String sql,
			PreparedStatement ps) throws SQLException {
		int count = StringHelper.containsCount(sql, "?");
		for (int i = 1; i <= count; i++) {
			long random = new Random(System.currentTimeMillis()+startTimes++).nextInt() * 30 + System.currentTimeMillis() + startTimes;
			try {
				ps.setLong(i, random);
			} catch (SQLException e) {
				try {
					ps.setInt(i, (int) random % Integer.MAX_VALUE);
				} catch (SQLException e1) {
					try {
						ps.setString(i, "" + random);
					} catch (SQLException e2) {
						try {
							ps.setTimestamp(i, new java.sql.Timestamp(random));
						} catch (SQLException e3) {
							try {
								ps.setDate(i, new java.sql.Date(random));
							} catch (SQLException e6) {
								try {
									ps.setString(i, "" + (int) random);
								} catch (SQLException e4) {
									try {
										ps.setString(i, "" + (short) random);
									} catch (SQLException e82) {
										try {
											ps.setString(i, "" + (byte) random);
										} catch (SQLException e32) {
										    try {
										        ps.setNull(i, java.sql.Types.OTHER);
										    }catch(SQLException error){
										        warn(sql, i, error);
										    }
										}
									}
								}
							}
						}
					}
				}

			}
		}
	}

	private static void warn(String sql, int i, SQLException error) {
		GLogger.warn("error on set parametet index:" + i + " cause:" + error
				+ " sql:" + sql);
	}
}
