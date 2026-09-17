/* This file is part of the ili2ora project.
 * For more information, please see <http://www.interlis.ch>.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package ch.ehi.ili2ofgdb;

import ch.ehi.basics.settings.Settings;
import ch.ehi.ili2db.converter.AbstractWKBColumnConverter;
import ch.ehi.ili2db.converter.ConverterException;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.sqlgen.generator_impl.ofgdb.GeneratorOfgdb;

import java.io.IOException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;

import com.vividsolutions.jts.io.ParseException;

import ch.interlis.iom.IomObject;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.interlis.iox_j.wkb.Iox2wkbException;
import net.iharder.Base64;

public class OfgdbColumnConverter extends AbstractWKBColumnConverter {
    private static final String BYTE_LITERAL_PREFIX = "__OFGDB_BYTES_B64__:";
	@Override
	public Integer getSrsid(String crsAuthority, String crsCode, Connection conn)
			throws ConverterException {
		Integer srsid=GeneratorOfgdb.getSrsId(crsAuthority, crsCode);
		return srsid;
	}
	private boolean strokeArcs=true;
	@Override
	public void setup(Connection conn, Settings config) {
		super.setup(conn,config);
		strokeArcs=Config.STROKE_ARCS_ENABLE.equals(Config.getStrokeArcs(config));
	}

	private byte[] sanitizeStrokeArcs3d(byte[] wkb, boolean is3D) throws ConverterException {
		if(wkb==null || !strokeArcs || !is3D) {
			return wkb;
		}
		return OfgdbStrokeZSanitizer.sanitizeNaNZToZero(wkb);
	}

	private byte[] asBytes(Object value, String sqlAttrName) throws ConverterException {
		if(value==null) {
			return null;
		}
		if(value instanceof byte[]) {
			return (byte[]) value;
		}
		if(value instanceof String) {
			String text=(String)value;
			if(text.startsWith(BYTE_LITERAL_PREFIX)) {
				try {
					return java.util.Base64.getDecoder().decode(text.substring(BYTE_LITERAL_PREFIX.length()));
				}catch(IllegalArgumentException e) {
					throw new ConverterException(e);
				}
			}
			try {
				return Base64.decode(text);
			} catch (IOException e) {
				throw new ConverterException(e);
			}
		}
		throw new ConverterException("expected binary value for "+sqlAttrName+" but got "+value.getClass().getName());
	}

	@Override
	public String getInsertValueWrapperCoord(String wkfValue,int srid) {
		return wkfValue;
	}
	@Override
	public String getInsertValueWrapperMultiCoord(String wkfValue,int srid) {
		return wkfValue;
	}
	@Override
	public String getInsertValueWrapperPolyline(String wkfValue,int srid) {
		return wkfValue;
	}
	@Override
	public String getInsertValueWrapperMultiPolyline(String wkfValue,int srid) {
		return wkfValue;
	}
	@Override
	public String getInsertValueWrapperSurface(String wkfValue,int srid) {
		return wkfValue;
	}
	@Override
	public String getInsertValueWrapperMultiSurface(String wkfValue,int srid) {
		return wkfValue;
	}
	@Override
	public String getSelectValueWrapperDate(String sqlColName) {
		 return sqlColName;
	}

	@Override
	public String getSelectValueWrapperTime(String sqlColName) {
		 return sqlColName;
	}

	@Override
	public String getSelectValueWrapperDateTime(String sqlColName) {
		 return sqlColName;
	}
	@Override
	public String getSelectValueWrapperCoord(String dbNativeValue) {
		return dbNativeValue;
	}
	@Override
	public String getSelectValueWrapperMultiCoord(String dbNativeValue) {
		return dbNativeValue;
	}
	@Override
	public String getSelectValueWrapperPolyline(String dbNativeValue) {
		return dbNativeValue;
	}
	@Override
	public String getSelectValueWrapperMultiPolyline(String dbNativeValue) {
		return dbNativeValue;
	}
	@Override
	public String getSelectValueWrapperSurface(String dbNativeValue) {
		return dbNativeValue;
	}
	@Override
	public String getSelectValueWrapperMultiSurface(String dbNativeValue) {
		return dbNativeValue;
	}
	@Override
	public Object fromIomUuid(String uuid) 
			throws java.sql.SQLException, ConverterException
	{
		return uuid;
	}
	@Override
	public Object fromIomXml(String xml) 
			throws java.sql.SQLException, ConverterException
	{
		return xml;
	}
	@Override
	public Object fromIomBlob(String blob) 
			throws java.sql.SQLException, ConverterException
	{

	    byte[] bytearray;
		try {
			bytearray = Base64.decode(blob);
		} catch (IOException e) {
			throw new ConverterException(e);
		}
		return bytearray;
	}
	@Override
	public java.lang.Object fromIomSurface(
			IomObject value,
			int srsid,
			boolean hasLineAttr,
			boolean is3D,double p)
			throws SQLException, ConverterException {
				if(value!=null){
					Iox2wkb conv=new Iox2wkb(is3D?3:2);
					try {
						byte[] wkb=conv.surface2wkb(value,!strokeArcs,p,false);
						return sanitizeStrokeArcs3d(wkb,is3D);
					} catch (Iox2wkbException ex) {
						throw new ConverterException(ex);
					}
				}
				return null;
		}
	@Override
	public java.lang.Object fromIomMultiSurface(
			IomObject value,
			int srsid,
			boolean hasLineAttr,
			boolean is3D,double p)
			throws SQLException, ConverterException {
				if(value!=null){
					Iox2wkb conv=new Iox2wkb(is3D?3:2);
					try {
						byte[] wkb=conv.multisurface2wkb(value,!strokeArcs,p,false);
						return sanitizeStrokeArcs3d(wkb,is3D);
					} catch (Iox2wkbException ex) {
						throw new ConverterException(ex);
					}
				}
				return null;
		}
		@Override
		public java.lang.Object fromIomCoord(IomObject value, int srsid,boolean is3D)
			throws SQLException, ConverterException {
			if(value!=null){
				Iox2wkb conv=new Iox2wkb(is3D?3:2);
				try {
					return conv.coord2wkb(value);
				} catch (Iox2wkbException ex) {
					throw new ConverterException(ex);
				}
			}
			return null;
		}
		@Override
		public java.lang.Object fromIomMultiCoord(IomObject value, int srsid,boolean is3D)
			throws SQLException, ConverterException {
			if(value!=null){
				Iox2wkb conv=new Iox2wkb(is3D?3:2);
				try {
					return conv.multicoord2wkb(value);
				} catch (Iox2wkbException ex) {
					throw new ConverterException(ex);
				}
			}
			return null;
		}
		@Override
	public java.lang.Object fromIomPolyline(IomObject value, int srsid,boolean is3D,double p)
			throws SQLException, ConverterException {
			if(value!=null){
				Iox2wkb conv=new Iox2wkb(is3D?3:2);
				try {
					byte[] wkb=conv.polyline2wkb(value,false,!strokeArcs,p);
					return sanitizeStrokeArcs3d(wkb,is3D);
				} catch (Iox2wkbException ex) {
					throw new ConverterException(ex);
				}
			}
			return null;
		}
		@Override
	public java.lang.Object fromIomMultiPolyline(IomObject value, int srsid,boolean is3D,double p)
			throws SQLException, ConverterException {
			if(value!=null){
				Iox2wkb conv=new Iox2wkb(is3D?3:2);
				try {
					byte[] wkb=conv.multiline2wkb(value,!strokeArcs,p);
					return sanitizeStrokeArcs3d(wkb,is3D);
				} catch (Iox2wkbException ex) {
					throw new ConverterException(ex);
				}
			}
			return null;
		}
		@Override
		public IomObject toIomCoord(
				Object geomobj,
				String sqlAttrName,
				boolean is3D)
				throws SQLException, ConverterException {
				byte bv[]=asBytes(geomobj,sqlAttrName);
				if(bv==null){
					return null;
				}
				OfgdbWkb2iox conv=new OfgdbWkb2iox();
				try {
					return conv.read(bv);
				} catch (ParseException e) {
					throw new ConverterException(e);
				}
			}
		@Override
		public IomObject toIomMultiCoord(
			Object geomobj,
			String sqlAttrName,
			boolean is3D)
			throws SQLException, ConverterException {
			byte bv[]=asBytes(geomobj,sqlAttrName);
			if(bv==null){
				return null;
			}
			OfgdbWkb2iox conv=new OfgdbWkb2iox();
			try {
				return conv.read(bv);
			} catch (ParseException e) {
				throw new ConverterException(e);
			}
		}
		@Override
			public IomObject toIomSurface(
				Object geomobj,
				String sqlAttrName,
				boolean is3D)
				throws SQLException, ConverterException {
				byte bv[]=asBytes(geomobj,sqlAttrName);
				if(bv==null){
					return null;
				}
				OfgdbWkb2iox conv=new OfgdbWkb2iox();
				try {
					return conv.read(bv);
				} catch (ParseException e) {
					throw new ConverterException(e);
				}
			}
		@Override
		public IomObject toIomMultiSurface(
			Object geomobj,
			String sqlAttrName,
			boolean is3D)
			throws SQLException, ConverterException {
			byte bv[]=asBytes(geomobj,sqlAttrName);
			if(bv==null){
				return null;
			}
			OfgdbWkb2iox conv=new OfgdbWkb2iox();
			try {
				return conv.read(bv);
			} catch (ParseException e) {
				throw new ConverterException(e);
			}
		}
		@Override
			public IomObject toIomPolyline(
				Object geomobj,
				String sqlAttrName,
				boolean is3D)
				throws SQLException, ConverterException {
				byte bv[]=asBytes(geomobj,sqlAttrName);
				if(bv==null){
					return null;
				}
				OfgdbWkb2iox conv=new OfgdbWkb2iox();
				try {
					return conv.read(bv);
				} catch (ParseException e) {
					throw new ConverterException(e);
				}
			}
		@Override
		public IomObject toIomMultiPolyline(
			Object geomobj,
			String sqlAttrName,
			boolean is3D)
			throws SQLException, ConverterException {
			byte bv[]=asBytes(geomobj,sqlAttrName);
			if(bv==null){
				return null;
			}
			OfgdbWkb2iox conv=new OfgdbWkb2iox();
			try {
				return conv.read(bv);
			} catch (ParseException e) {
				throw new ConverterException(e);
			}
		}
		@Override
		public String toIomXml(Object obj) throws java.sql.SQLException,
				ConverterException {
			return (String)obj;
		}

		@Override
		public String toIomBlob(Object obj) throws java.sql.SQLException,
				ConverterException {
		    byte[] bytes=asBytes(obj,"blob");
		    if(bytes==null){
		    	return null;
		    }
		    String s = Base64.encodeBytes(bytes);
		    return s;
		}

		@Override
		public void setTimestamp(PreparedStatement ps, int valuei,
				Timestamp datetime) throws SQLException {
			ps.setString(valuei, datetime.toString().replace('T', ' '));
		}

		@Override
		public void setDate(PreparedStatement ps, int valuei, Date date)
				throws SQLException {
			ps.setString(valuei, date.toString());
		}

		@Override
		public void setTime(PreparedStatement ps, int valuei, Time time)
				throws SQLException {
			ps.setString(valuei, "1970-01-01 " + time.toString());
		}

		@Override
		public void setXmlNull(PreparedStatement stmt, int parameterIndex)
				throws SQLException {
			 stmt.setNull(parameterIndex, Types.VARCHAR);
		}
		@Override
		public void setBlobNull(PreparedStatement stmt, int parameterIndex)
				throws SQLException {
			 stmt.setNull(parameterIndex, Types.VARBINARY);
		}

}
