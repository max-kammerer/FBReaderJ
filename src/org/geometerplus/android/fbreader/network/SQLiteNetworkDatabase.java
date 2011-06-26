/*
 * Copyright (C) 2010-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.network;

import java.util.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import org.geometerplus.zlibrary.ui.android.library.ZLAndroidApplication;

import org.geometerplus.fbreader.network.*;
import org.geometerplus.fbreader.network.urlInfo.*;

import org.geometerplus.android.util.SQLiteUtil;

class SQLiteNetworkDatabase extends NetworkDatabase {
	private final SQLiteDatabase myDatabase;

	SQLiteNetworkDatabase() {
		myDatabase = ZLAndroidApplication.Instance().openOrCreateDatabase("network.db", Context.MODE_PRIVATE, null);
		migrate();
	}

	private void migrate() {
		final int version = myDatabase.getVersion();
		final int currentCodeVersion = 5;
		if (version >= currentCodeVersion) {
			return;
		}
		myDatabase.beginTransaction();
		switch (version) {
			case 0:
				createTables();
			case 1:
				updateTables1();
			case 2:
				updateTables2();
			case 3:
				updateTables3();
			case 4:
				updateTables4();
		}
		myDatabase.setTransactionSuccessful();
		myDatabase.endTransaction();

		myDatabase.execSQL("VACUUM");
		myDatabase.setVersion(currentCodeVersion);
	}

	protected void executeAsATransaction(Runnable actions) {
		myDatabase.beginTransaction();
		try {
			actions.run();
			myDatabase.setTransactionSuccessful();
		} finally {
			myDatabase.endTransaction();
		}
	}

	@Override
	protected List<INetworkLink> listLinks() {
		final List<INetworkLink> links = new LinkedList<INetworkLink>();

		final Cursor cursor = myDatabase.rawQuery("SELECT link_id,title,site_name,summary,is_predefined,is_enabled FROM Links", null);
		final UrlInfoCollection<UrlInfoWithDate> linksMap = new UrlInfoCollection<UrlInfoWithDate>();
		while (cursor.moveToNext()) {
			final int id = cursor.getInt(0);
			final String title = cursor.getString(1);
			final String catalogId = cursor.getString(2);
			final String summary = cursor.getString(3);
			final boolean isPredefined = cursor.getInt(4) > 0;
			final boolean isEnabled = cursor.getInt(5) > 0;

			linksMap.clear();
			final Cursor linksCursor = myDatabase.rawQuery("SELECT key,url,update_time FROM LinkUrls WHERE link_id = " + id, null);
			while (linksCursor.moveToNext()) {
				try {
					linksMap.addInfo(
						new UrlInfoWithDate(
							UrlInfo.Type.valueOf(linksCursor.getString(0)),
							linksCursor.getString(1),
							SQLiteUtil.getDate(linksCursor, 2)
						)
					);
				} catch (IllegalArgumentException e) {
				}
			}
			linksCursor.close();

			final INetworkLink l = createLink(id, siteName, title, summary, linksMap);
			if (l != null) {
				links.add(l);
			}
		}
		cursor.close();

		return links;
	}

	private SQLiteStatement myInsertCustomLinkStatement;
	private SQLiteStatement myUpdateCustomLinkStatement;
	private SQLiteStatement myInsertCustomLinkUrlStatement;
	private SQLiteStatement myUpdateCustomLinkUrlStatement;
	private SQLiteStatement myDeleteCustomLinkUrlStatement;
	@Override
	protected void saveLink(final INetworkLink link) {
		executeAsATransaction(new Runnable() {
			public void run() {
				final SQLiteStatement statement;
				if (link.getId() == INetworkLink.INVALID_ID) {
					if (myInsertCustomLinkStatement == null) {
						myInsertCustomLinkStatement = myDatabase.compileStatement(
							"INSERT INTO Links (title,site_name,summary,is_predefined,is_enabled) VALUES (?,?,?,?,?)"
						);
					}
					statement = myInsertCustomLinkStatement;
				} else {
					if (myUpdateCustomLinkStatement == null) {
						myUpdateCustomLinkStatement = myDatabase.compileStatement(
							"UPDATE Links SET title = ?, site_name = ?, summary =? "
								+ "WHERE link_id = ?"
						);
					}
					statement = myUpdateCustomLinkStatement;
				}

				statement.bindString(1, link.getTitle());
				statement.bindString(2, link.getSiteName());
				SQLiteUtil.bindString(statement, 3, link.getSummary());

				final long id;
				final UrlInfoCollection<UrlInfoWithDate> linksMap =
					new UrlInfoCollection<UrlInfoWithDate>();

				if (statement == myInsertCustomLinkStatement) {
					statement.bindLong(4, link instanceof ICustomNetworkLink ? 0 : 1);
					statement.bindLong(5, 1);
					id = statement.executeInsert();
					link.setId((int) id);
				} else {
					id = link.getId();
					statement.bindLong(4, id);
					statement.execute();
					
					final Cursor linksCursor = myDatabase.rawQuery("SELECT key,url,update_time FROM LinkUrls WHERE link_id = " + link.getId(), null);
					while (linksCursor.moveToNext()) {
						try {
							linksMap.addInfo(
								new UrlInfoWithDate(
									UrlInfo.Type.valueOf(linksCursor.getString(0)),
									linksCursor.getString(1),
									SQLiteUtil.getDate(linksCursor, 2)
								)
							);
						} catch (IllegalArgumentException e) {
						}
					}
					linksCursor.close();
				}

				for (UrlInfo.Type key : link.getUrlKeys()) {
					final UrlInfoWithDate info = link.getUrlInfo(key);
					final UrlInfoWithDate dbInfo = linksMap.getInfo(key);
					linksMap.removeAllInfos(key);
					final SQLiteStatement urlStatement;
					if (dbInfo == null) {
						if (myInsertCustomLinkUrlStatement == null) {
							myInsertCustomLinkUrlStatement = myDatabase.compileStatement(
									"INSERT OR REPLACE INTO LinkUrls(url,update_time,link_id,key) VALUES (?,?,?,?)");
						}
						urlStatement = myInsertCustomLinkUrlStatement;
					} else if (!info.equals(dbInfo)) {
						if (myUpdateCustomLinkUrlStatement == null) {
							myUpdateCustomLinkUrlStatement = myDatabase.compileStatement(
									"UPDATE LinkUrls SET url = ?, update_time = ? WHERE link_id = ? AND key = ?");
						}
						urlStatement = myUpdateCustomLinkUrlStatement;
					} else {
						continue;
					}
					SQLiteUtil.bindString(urlStatement, 1, info.Url);
					SQLiteUtil.bindDate(urlStatement, 2, info.Updated);
					urlStatement.bindLong(3, id);
					urlStatement.bindString(4, key.toString());
					urlStatement.execute();
				}
				for (UrlInfo info : linksMap.getAllInfos()) {
					if (myDeleteCustomLinkUrlStatement == null) {
						myDeleteCustomLinkUrlStatement = myDatabase.compileStatement(
								"DELETE FROM LinkUrls WHERE link_id = ? AND key = ?");
					}
					myDeleteCustomLinkUrlStatement.bindLong(1, id);
					myDeleteCustomLinkUrlStatement.bindString(2, info.InfoType.toString());
					myDeleteCustomLinkUrlStatement.execute();
				}
			}
		});
	}

	@Override
	protected void deleteLink(final INetworkLink link) {
		if (link.getId() == INetworkLink.INVALID_ID) {
			return;
		}
		executeAsATransaction(new Runnable() {
			public void run() {
				final String stringLinkId = String.valueOf(link.getId());
				myDatabase.rawQuery(
					"DELETE FROM LinkUrls WHERE link_id = ?",
					new String[] { stringLinkId }
				);
				myDatabase.rawQuery(
					"DELETE FROM Links WHERE link_id = ?",
					new String[] { stringLinkId }
				);
				link.setId(INetworkLink.INVALID_ID);
			}
		});
	}

	@Override
	protected Map<String,String> getLinkExtras(INetworkLink link) {
		final HashMap<String,String> extras = new HashMap<String,String>();
		final Cursor cursor = myDatabase.rawQuery(
			"SELECT key,value FROM Extras WHERE link_id = ?",
			new String[] { String.valueOf(link.getId()) }
		);
		while (cursor.moveToNext()) {
			extras.put(cursor.getString(0), cursor.getString(1));
		}
		return extras;
	}

	@Override
	protected void setLinkExtras(INetworkLink link, Map<String,String> extras) {
		if (link.getId() == INetworkLink.INVALID_ID) {
			return;
		}
		final String stringLinkId = String.valueOf(link.getId());
		myDatabase.rawQuery(
			"DELETE FROM Extras WHERE link_id = ?",
			new String[] { stringLinkId }
		);
		for (Map.Entry<String,String> entry : extras.entrySet()) {
			myDatabase.rawQuery(
				"INSERT INTO Extras (link_id,key,value) VALUES (?,?,?)",
				new String[] { stringLinkId, entry.getKey(), entry.getValue() }
			);
		}
	}
	
	private void createTables() {
		myDatabase.execSQL(
				"CREATE TABLE CustomLinks(" +
					"link_id INTEGER PRIMARY KEY," +
					"title TEXT UNIQUE NOT NULL," +
					"site_name TEXT NOT NULL," +
					"summary TEXT," +
					"icon TEXT)");
		myDatabase.execSQL(
				"CREATE TABLE CustomLinkUrls(" +
					"key TEXT NOT NULL," +
					"link_id INTEGER NOT NULL REFERENCES CustomLinks(link_id)," +
					"url TEXT NOT NULL," +
					"CONSTRAINT CustomLinkUrls_PK PRIMARY KEY (key, link_id))");
	}

	private void updateTables1() {
		myDatabase.execSQL("ALTER TABLE CustomLinks RENAME TO CustomLinks_Obsolete");
		myDatabase.execSQL(
				"CREATE TABLE CustomLinks(" +
					"link_id INTEGER PRIMARY KEY," +
					"title TEXT NOT NULL," +
					"site_name TEXT NOT NULL," +
					"summary TEXT," +
					"icon TEXT)");
		myDatabase.execSQL("INSERT INTO CustomLinks (link_id,title,site_name,summary,icon) SELECT link_id,title,site_name,summary,icon FROM CustomLinks_Obsolete");
		myDatabase.execSQL("DROP TABLE CustomLinks_Obsolete");

		myDatabase.execSQL(
				"CREATE TABLE LinkUrls(" +
					"key TEXT NOT NULL," +
					"link_id INTEGER NOT NULL REFERENCES CustomLinks(link_id)," +
					"url TEXT," +
					"update_time INTEGER," +
					"CONSTRAINT LinkUrls_PK PRIMARY KEY (key, link_id))");
		myDatabase.execSQL("INSERT INTO LinkUrls (key,link_id,url) SELECT key,link_id,url FROM CustomLinkUrls");
		myDatabase.execSQL("DROP TABLE CustomLinkUrls");
	}

	private void updateTables2() {
		myDatabase.execSQL(
				"CREATE TABLE Links(" +
					"link_id INTEGER PRIMARY KEY," +
					"title TEXT NOT NULL," +
					"site_name TEXT NOT NULL," +
					"summary TEXT)");
		myDatabase.execSQL("INSERT INTO Links (link_id,title,site_name,summary) SELECT link_id,title,site_name,summary FROM CustomLinks");
		final Cursor cursor = myDatabase.rawQuery("SELECT link_id,icon FROM CustomLinks", null);
		while (cursor.moveToNext()) {
			final int id = cursor.getInt(0);
			final String url = cursor.getString(1);
			myDatabase.execSQL("INSERT INTO LinkUrls (key,link_id,url) VALUES " +
				"('icon'," + id + ",'" + url + "')");
		}
		cursor.close();
		myDatabase.execSQL("DROP TABLE CustomLinks");
	}

	private void updateTables3() {
		myDatabase.execSQL("UPDATE LinkUrls SET key='Catalog' WHERE key='main'");
		myDatabase.execSQL("UPDATE LinkUrls SET key='Search' WHERE key='search'");
		myDatabase.execSQL("UPDATE LinkUrls SET key='Image' WHERE key='icon'");
	}

	private void updateTables4() {
		myDatabase.execSQL("ALTER TABLE Links ADD COLUMN is_predefined INTEGER");
		myDatabase.execSQL("UPDATE Links SET is_predefined=0");

		myDatabase.execSQL("ALTER TABLE Links ADD COLUMN is_enabled INTEGER DEFAULT 1");

		myDatabase.execSQL("ALTER TABLE LinkUrls RENAME TO LinkUrls_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE LinkUrls(" +
				"key TEXT NOT NULL," +
				"link_id INTEGER NOT NULL REFERENCES Links(link_id)," +
				"url TEXT," +
				"update_time INTEGER," +
				"CONSTRAINT LinkUrls_PK PRIMARY KEY (key, link_id))"
		);
		myDatabase.execSQL("INSERT INTO LinkUrls (key,link_id,url) SELECT key,link_id,url FROM LinkUrls_Obsolete");
		myDatabase.execSQL("DROP TABLE LinkUrls_Obsolete");

		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Extras(" +
				"link_id INTEGER NOT NULL REFERENCES Links(link_id)," +
				"key TEXT NOT NULL," +
				"value TEXT NOT NULL," +
				"CONSTRAINT Extras_PK PRIMARY KEY (key, link_id))"
		);
	}

	private void updateTables5() {
		myDatabase.execSQL("ALTER TABLE Links RENAME TO Links_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE Links(" +
				"link_id INTEGER PRIMARY KEY," +
				"catalog_id TEXT UNIQUE," +
				"title TEXT NOT NULL," +
				"summary TEXT," +
				"is_predefined INTEGER," +
				"is_enabled INTEGER");
		myDatabase.execSQL("INSERT INTO Links (link_id,catalog_id,title,summary,is_predefined,is_enabled) SELECT link_id,site_name,title,summary,is_predefined,is_enabled FROM Links_Obsolete");
		myDatabase.execSQL("DROP TABLE Links_Obsolete");
	}
}
