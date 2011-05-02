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

package org.geometerplus.fbreader.network.atom;

import java.util.Map;

import org.geometerplus.zlibrary.core.constants.XMLNamespaces;
import org.geometerplus.zlibrary.core.xml.ZLStringMap;
import org.geometerplus.zlibrary.core.xml.ZLXMLReaderAdapter;

import org.geometerplus.android.fbreader.tips.TipsKeys;
import org.geometerplus.fbreader.network.opds.HtmlToString;

import android.util.Log;

public class ATOMXMLReader extends ZLXMLReaderAdapter {
	public static String intern(String str) {
		if (str == null || str.length() == 0) {
			return null;
		}
		return str.intern();
	}

	private final ATOMFeedHandler myFeedHandler;

	private ATOMFeedMetadata myFeed;
	private ATOMEntry myEntry;
	private ATOMAuthor myAuthor;
	private ATOMId myId;
	private ATOMLink myLink;
	private ATOMCategory myCategory;
	private ATOMUpdated myUpdated;
	private ATOMPublished myPublished;
	private ATOMIcon myIcon;

	private Map<String,String> myNamespaceMap;

	protected static final int START = 0;
	protected static final int FEED = 1;
	protected static final int F_ENTRY = 2;
	protected static final int F_ID = 3;
	protected static final int F_LINK = 4;
	protected static final int F_CATEGORY = 5;
	protected static final int F_TITLE = 6;
	protected static final int F_UPDATED = 7;
	protected static final int F_AUTHOR = 8;
	protected static final int FA_NAME = 9;
	protected static final int FA_URI = 10;
	protected static final int FA_EMAIL = 11;
	protected static final int FE_AUTHOR = 12;
	protected static final int FE_ID = 13;
	protected static final int FE_CATEGORY = 14;
	protected static final int FE_LINK = 15;
	protected static final int FE_PUBLISHED = 16;
	protected static final int FE_SUMMARY = 17;
	protected static final int FE_CONTENT = 18;
	protected static final int FE_TITLE = 19;
	protected static final int FE_UPDATED = 20;
	protected static final int FEA_NAME = 21;
	protected static final int FEA_URI = 22;
	protected static final int FEA_EMAIL = 23;
	protected static final int F_SUBTITLE = 24;
	protected static final int F_ICON = 25;

	protected static final int ATOM_STATE_FIRST_UNUSED = 26;

	protected static final String TAG_FEED = "feed";
	protected static final String TAG_ENTRY = "entry";
	protected static final String TAG_AUTHOR = "author";
	protected static final String TAG_NAME = "name";
	protected static final String TAG_URI = "uri";
	protected static final String TAG_EMAIL = "email";
	protected static final String TAG_ID = "id";
	protected static final String TAG_CATEGORY = "category";
	protected static final String TAG_LINK = "link";
	protected static final String TAG_PUBLISHED = "published";
	protected static final String TAG_SUMMARY = "summary";
	protected static final String TAG_CONTENT = "content";
	protected static final String TAG_TITLE = "title";
	protected static final String TAG_UPDATED = "updated";
	protected static final String TAG_SUBTITLE = "subtitle";
	protected static final String TAG_ICON = "icon";

	protected int myState;
	private final StringBuilder myBuffer = new StringBuilder();
	protected HtmlToString myHtmlToString = new HtmlToString();
	protected boolean myFeedMetadataProcessed;

	public ATOMXMLReader(ATOMFeedHandler handler, boolean readEntryNotFeed) {
		myFeedHandler = handler;
		myState = readEntryNotFeed ? FEED : START;
	}

	protected final ATOMFeedHandler getATOMFeedHandler() {
		return myFeedHandler;
	}

	protected final ATOMFeedMetadata getATOMFeed() {
		return myFeed;
	}

	protected final ATOMEntry getATOMEntry() {
		return myEntry;
	}

	protected final ATOMLink getATOMLink() {
		return myLink;
	}

	@Override
	public final boolean processNamespaces() {
		return true;
	}

	@Override
	public final void namespaceMapChangedHandler(Map<String,String> namespaceMap) {
		myNamespaceMap = namespaceMap;
	}

	protected final String getNamespace(String prefix) {
		if (myNamespaceMap == null) {
			return null;
		}
		final String ns = myNamespaceMap.get(prefix);
		return ns != null ? ns.intern() : null;
	}

	@Override
	public final boolean startElementHandler(String tag, ZLStringMap attributes) {
		final int index = tag.indexOf(':');
		final String tagPrefix;
		if (index != -1) {
			tagPrefix = tag.substring(0, index).intern();
			tag = tag.substring(index + 1).intern();
		} else {
			tagPrefix = "";
			tag = tag.intern();
		}
		return startElementHandler(getNamespace(tagPrefix), tag, attributes, extractBufferContent());
	}

	@Override
	public final boolean endElementHandler(String tag) {
		final int index = tag.indexOf(':');
		final String tagPrefix;
		if (index != -1) {
			tagPrefix = tag.substring(0, index).intern();
			tag = tag.substring(index + 1).intern();
		} else {
			tagPrefix = "";
			tag = tag.intern();
		}
		return endElementHandler(getNamespace(tagPrefix), tag, extractBufferContent());
	}

	private final String extractBufferContent() {
		final char[] bufferContentArray = myBuffer.toString().trim().toCharArray();
		myBuffer.delete(0, myBuffer.length());
		if (bufferContentArray.length == 0) {
			return null;
		}
		return new String(bufferContentArray);
	}

	protected ATOMFeedMetadata createFeed(ZLStringMap attributes) {
		return new ATOMFeedMetadata(attributes);
	}

	protected ATOMLink createLink(ZLStringMap attributes) {
		return new ATOMLink(attributes);
	}

	protected ATOMEntry createEntry(ZLStringMap attributes) {
		return new ATOMEntry(attributes);
	}

	public boolean startElementHandler(
		final String ns, final String tag,
		final ZLStringMap attributes, final String bufferContent
	) {
		boolean interruptReading = false;
		switch (myState) {
			case START:
				if (ns == XMLNamespaces.Atom && tag == TAG_FEED) {
					myFeedHandler.processFeedStart();
					myFeed = createFeed(attributes);
					myState = FEED;
					myFeedMetadataProcessed = false;
				}
				break;
			case FEED:
				if (ns == XMLNamespaces.Atom) {
					if (tag == TAG_AUTHOR) {
						myAuthor = new ATOMAuthor(attributes);
						myState = F_AUTHOR;
					} else if (tag == TAG_ID) {
						myId = new ATOMId(attributes);
						myState = F_ID;
					} else if (tag == TAG_ICON) {
						myIcon = new ATOMIcon(attributes);
						myState = F_ICON;
					} else if (tag == TAG_LINK) {
						myLink = createLink(attributes);			// TODO
						myState = F_LINK;
					} else if (tag == TAG_CATEGORY) {
						myCategory = new ATOMCategory(attributes);
						myState = F_CATEGORY;
					} else if (tag == TAG_TITLE) {
						//myTitle = new ATOMTitle(attributes); // TODO:implement ATOMTextConstruct & ATOMTitle
						myHtmlToString.setupTextContent(attributes.getValue("type"));
						myState = F_TITLE;
					} else if (tag == TAG_SUBTITLE) {
						//mySubtitle = new ATOMTitle(attributes); // TODO:implement ATOMTextConstruct & ATOMSubtitle
						myHtmlToString.setupTextContent(attributes.getValue("type"));
						myState = F_SUBTITLE;
					} else if (tag == TAG_UPDATED) {
						myUpdated = new ATOMUpdated(attributes);
						myState = F_UPDATED;
					} else if (tag == TAG_ENTRY) {
						myEntry = createEntry(attributes);
						myState = F_ENTRY;
						// Process feed metadata just before first feed entry
						if (myFeed != null && !myFeedMetadataProcessed) {
							interruptReading = myFeedHandler.processFeedMetadata(myFeed, true);
							myFeedMetadataProcessed = true;
						}
					}
				}
				break;
			case F_ENTRY:
				if (ns == XMLNamespaces.Atom) {
					if (tag == TAG_AUTHOR) {
						myAuthor = new ATOMAuthor(attributes);
						myState = FE_AUTHOR;
					} else if (tag == TAG_ID) {
						myId = new ATOMId(attributes);
						myState = FE_ID;
					} else if (tag == TAG_CATEGORY) {
						myCategory = new ATOMCategory(attributes);
						myState = FE_CATEGORY;
					} else if (tag == TAG_LINK) {
						myLink = createLink(attributes);				// TODO
						myState = FE_LINK;
					} else if (tag == TAG_PUBLISHED) {
						myPublished = new ATOMPublished(attributes);
						myState = FE_PUBLISHED;
					} else if (tag == TAG_SUMMARY) {
						//mySummary = new ATOMSummary(attributes); // TODO:implement ATOMTextConstruct & ATOMSummary
						myHtmlToString.setupTextContent(attributes.getValue("type"));
						myState = FE_SUMMARY;
					} else if (tag == TAG_CONTENT) {
						//myConent = new ATOMContent(attributes); // TODO:implement ATOMContent
						myHtmlToString.setupTextContent(attributes.getValue("type"));
						myState = FE_CONTENT;
					} else if (tag == TAG_TITLE) {
						//myTitle = new ATOMTitle(attributes); // TODO:implement ATOMTextConstruct & ATOMTitle
						myHtmlToString.setupTextContent(attributes.getValue("type"));
						myState = FE_TITLE;
					} else if (tag == TAG_UPDATED) {
						myUpdated = new ATOMUpdated(attributes);
						myState = FE_UPDATED;
					}
				}
				break;
			case F_AUTHOR:
				if (ns == XMLNamespaces.Atom) {
					if (tag == TAG_NAME) {
						myState = FA_NAME;
					} else if (tag == TAG_URI) {
						myState = FA_URI;
					} else if (tag == TAG_EMAIL) {
						myState = FA_EMAIL;
					}
				}
				break;
			case FE_AUTHOR:
				if (ns == XMLNamespaces.Atom) {
					if (tag == TAG_NAME) {
						myState = FEA_NAME;
					} else if (tag == TAG_URI) {
						myState = FEA_URI;
					} else if (tag == TAG_EMAIL) {
						myState = FEA_EMAIL;
					}
				}
				break;
			case FE_SUMMARY:
			case FE_TITLE:
			case F_TITLE:
			case F_SUBTITLE:
				myHtmlToString.processTextContent(false, tag, attributes, bufferContent);
				break;
			default:
				break;
		}

		return interruptReading;
	}

	public boolean endElementHandler(final String ns, final String tag,
			final String bufferContent) {
		boolean interruptReading = false;
		switch (myState) {
			case START:
				break;
			case FEED:
				if (ns == XMLNamespaces.Atom && tag == TAG_FEED) {
					if (myFeed != null) {
						interruptReading = myFeedHandler.processFeedMetadata(myFeed, false);
					}
					myFeed = null;
					myFeedHandler.processFeedEnd();
					myState = START;
				}
				break;
			case F_ENTRY:
				if (ns == XMLNamespaces.Atom && tag == TAG_ENTRY) {
					if (myEntry != null) {
						interruptReading = myFeedHandler.processFeedEntry(myEntry);
					}
					myEntry = null;
					myState = FEED;
				}
				break;
			case F_ID:
				if (ns == XMLNamespaces.Atom && tag == TAG_ID) {
					// FIXME:uri can be lost:buffer will be truncated, if there are extension tags inside the <id> tag
					if (bufferContent != null && myFeed != null) {
						myId.Uri = bufferContent;
						myFeed.Id = myId;
					}
					myId = null;
					myState = FEED;
				}
				break;
			case F_ICON:
				if (ns == XMLNamespaces.Atom && tag == TAG_ICON) {
					// FIXME:uri can be lost:buffer will be truncated, if there are extension tags inside the <id> tag
					if (bufferContent != null && myFeed != null) {
						myIcon.Uri = bufferContent;
						myFeed.Icon = myIcon;
					}
					myIcon = null;
					myState = FEED;
				}
				break;
			case F_LINK:
				if (ns == XMLNamespaces.Atom && tag == TAG_LINK) {
					if (myFeed != null) {
						myFeed.Links.add(myLink);
					}
					myLink = null;
					myState = FEED;
				}
				break;
			case F_CATEGORY:
				if (ns == XMLNamespaces.Atom && tag == TAG_CATEGORY) {
					if (myFeed != null) {
						myFeed.Categories.add(myCategory);
					}
					myCategory = null;
					myState = FEED;
				}
				break;
			case F_TITLE:
				if (ns == XMLNamespaces.Atom && tag == TAG_TITLE) {
					// TODO:implement ATOMTextConstruct & ATOMTitle
					final String title = myHtmlToString.finishTextContent(bufferContent);
					if (myFeed != null) {
						myFeed.Title = title;
					}
					myState = FEED;
				} else {
					myHtmlToString.processTextContent(true, tag, null, bufferContent);
				}
				break;
			case F_SUBTITLE:
				if (ns == XMLNamespaces.Atom && tag == TAG_SUBTITLE) {
					// TODO:implement ATOMTextConstruct & ATOMSubtitle
					final String subtitle = myHtmlToString.finishTextContent(bufferContent);
					if (myFeed != null) {
						myFeed.Subtitle = subtitle;
					}
					myState = FEED;
				} else {
					myHtmlToString.processTextContent(true, tag, null, bufferContent);
				}
				break;
			case F_UPDATED:
				if (ns == XMLNamespaces.Atom && tag == TAG_UPDATED) {
					// FIXME:uri can be lost:buffer will be truncated, if there are extension tags inside the <id> tag
					if (ATOMDateConstruct.parse(bufferContent, myUpdated) && myFeed != null) {
						myFeed.Updated = myUpdated;
					}
					myUpdated = null;
					myState = FEED;
				}
				break;
			case F_AUTHOR:
				if (ns == XMLNamespaces.Atom && tag == TAG_AUTHOR) {
					if (myFeed != null && myAuthor.Name != null) {
						myFeed.Authors.add(myAuthor);
					}
					myAuthor = null;
					myState = FEED;
				}
				break;
			case FA_NAME:
				if (ns == XMLNamespaces.Atom && tag == TAG_NAME) {
					myAuthor.Name = bufferContent;
					myState = F_AUTHOR;
				}
				break;
			case FEA_NAME:
				if (ns == XMLNamespaces.Atom && tag == TAG_NAME) {
					myAuthor.Name = bufferContent;
					myState = FE_AUTHOR;
				}
				break;
			case FA_URI:
				if (ns == XMLNamespaces.Atom && tag == TAG_URI) {
					myAuthor.Uri = bufferContent;
					myState = F_AUTHOR;
				}
				break;
			case FEA_URI:
				if (ns == XMLNamespaces.Atom && tag == TAG_URI) {
					myAuthor.Uri = bufferContent;
					myState = FE_AUTHOR;
				}
				break;
			case FA_EMAIL:
				if (ns == XMLNamespaces.Atom && tag == TAG_EMAIL) {
					myAuthor.Email = bufferContent;
					myState = F_AUTHOR;
				}
				break;
			case FEA_EMAIL:
				if (ns == XMLNamespaces.Atom && tag == TAG_EMAIL) {
					myAuthor.Email = bufferContent;
					myState = FE_AUTHOR;
				}
				break;
			case FE_AUTHOR:
				if (ns == XMLNamespaces.Atom && tag == TAG_AUTHOR) {
					if (myAuthor.Name != null) {
						myEntry.Authors.add(myAuthor);
					}
					myAuthor = null;
					myState = F_ENTRY;
				}
				break;
			case FE_ID:
				if (ns == XMLNamespaces.Atom && tag == TAG_ID) {
					// FIXME:uri can be lost:buffer will be truncated, if there are extension tags inside the <id> tag
					if (bufferContent != null) {
						myId.Uri = bufferContent;
						myEntry.Id = myId;
					}
					myId = null;
					myState = F_ENTRY;
				}
				break;
			case FE_CATEGORY:
				if (ns == XMLNamespaces.Atom && tag == TAG_CATEGORY) {
					myEntry.Categories.add(myCategory);
					myCategory = null;
					myState = F_ENTRY;
				}
				break;
			case FE_LINK:
				if (ns == XMLNamespaces.Atom && tag == TAG_LINK) {
					myEntry.Links.add(myLink);
					myLink = null;
					myState = F_ENTRY;
				}
				break;
			case FE_PUBLISHED:
				if (ns == XMLNamespaces.Atom && tag == TAG_PUBLISHED) {
					// FIXME:uri can be lost:buffer will be truncated, if there are extension tags inside the <id> tag
					if (ATOMDateConstruct.parse(bufferContent, myPublished)) {
						myEntry.Published = myPublished;
					}
					myPublished = null;
					myState = F_ENTRY;
				}
				break;
			case FE_SUMMARY:
				if (ns == XMLNamespaces.Atom && tag == TAG_SUMMARY) {
					// TODO:implement ATOMTextConstruct & ATOMSummary
					myEntry.Summary = myHtmlToString.finishTextContent(bufferContent);
					myState = F_ENTRY;
				} else {
					myHtmlToString.processTextContent(true, tag, null, bufferContent);
				}
				break;
			case FE_CONTENT:
				if (ns == XMLNamespaces.Atom && tag == TAG_CONTENT) {
					// TODO:implement ATOMContent
					myEntry.Content = myHtmlToString.finishTextContent(bufferContent);
					myState = F_ENTRY;
				} else {
					myHtmlToString.processTextContent(true, tag, null, bufferContent);
				}
				break;
			case FE_TITLE:
				if (ns == XMLNamespaces.Atom && tag == TAG_TITLE) {
					// TODO:implement ATOMTextConstruct & ATOMTitle
					myEntry.Title = myHtmlToString.finishTextContent(bufferContent);
					myState = F_ENTRY;
				} else {
					myHtmlToString.processTextContent(true, tag, null, bufferContent);
				}
				break;
			case FE_UPDATED:
				if (ns == XMLNamespaces.Atom && tag == TAG_UPDATED) {
					// FIXME:uri can be lost:buffer will be truncated, if there are extension tags inside the <id> tag
					if (ATOMDateConstruct.parse(bufferContent, myUpdated)) {
						myEntry.Updated = myUpdated;
					}
					myUpdated = null;
					myState = F_ENTRY;
				}
				break;
		}

		return interruptReading;
	}

	@Override
	public final void characterDataHandler(char[] data, int start, int length) {
		final int startIndex = myBuffer.length();
		myBuffer.append(data, start, length);
		int index = startIndex;
		while ((index = myBuffer.indexOf("\r\n", index)) != -1) {
			myBuffer.replace(index, index + 2, "\n");
		}
		index = startIndex;
		while ((index = myBuffer.indexOf("\r", index)) != -1) {
			myBuffer.setCharAt(index, '\n');
		}
	}

}
