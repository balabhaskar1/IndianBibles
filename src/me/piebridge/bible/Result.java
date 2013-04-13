/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2012, 2013 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.app.Activity;
import android.app.SearchManager;
import android.os.Bundle;

import android.view.View;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.AdapterView;
import android.widget.SimpleCursorAdapter;

import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.Locale;

public class Result extends Activity
{
    private final String TAG = "me.piebridge.bible$Search";

    private TextView textView = null;
    private ListView listView = null;;

    private String humanfrom;
    private String humanto;
    private String version = null;
    private String query = null;
    private Bible bible;
    private SimpleCursorAdapter adapter = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        bible = Bible.getBible(getBaseContext());
        version = bible.getVersion();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra(SearchManager.QUERY);
            String osisfrom = intent.getStringExtra("osisfrom");
            String osisto = intent.getStringExtra("osisto");
            Log.d(TAG, "query: " + query + ", osisfrom: " + osisfrom + ", osisto: " + osisto);
            doSearch(query, getQueryBooks(osisfrom, osisto));
        } else {
            finish();
        }
    }

    @Override
    public void onResume() {
        version = bible.getVersion();
        super.onResume();
    }

    private boolean doSearch(String query, String books) {
        setContentView(R.layout.result);
        textView = (TextView) findViewById(R.id.text);
        listView = (ListView) findViewById(R.id.list);
        if (version == null) {
            textView.setText(R.string.noversion);
            return false;
        }

        Log.d(TAG, "search \"" + query + "\" in version \"" + version + "\"");

        Uri uri = Provider.CONTENT_URI_SEARCH.buildUpon().appendQueryParameter("books", books).appendEncodedPath(query).fragment(version).build();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
        } catch (Exception e) {
        }

        if (cursor == null) {
            textView.setText(getString(R.string.search_no_results, new Object[] {
                query,
                humanfrom,
                humanto,
                String.valueOf(version).toUpperCase(Locale.US)
            }));
            return false;
        } else {
            int count = cursor.getCount();
            String countString = getResources().getQuantityString(R.plurals.search_results, count, new Object[] {
                count,
                query,
                humanfrom,
                humanto,
                String.valueOf(version).toUpperCase(Locale.US)
            });
            textView.setText(countString);
        }
        showResults(cursor);
        return true;
    }

    private void closeAdapter() {
        if (adapter != null) {
            Cursor cursor = adapter.getCursor();
            cursor.close();
            adapter = null;
        }
    }

    private void showResults(Cursor cursor) {

        String[] from = new String[] {
            Provider.COLUMN_HUMAN,
            Provider.COLUMN_VERSE,
            Provider.COLUMN_UNFORMATTED,
        };

        int[] to = new int[] {
            R.id.human,
            R.id.verse,
            R.id.unformatted,
        };

        closeAdapter();
        adapter = new SimpleCursorAdapter(this,
            R.layout.item, cursor, from, to);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                int verseIndex = cursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE);
                if (columnIndex == verseIndex) {
                    int[] chapterVerse = bible.getChapterVerse(cursor.getString(verseIndex));
                    String string = getString(R.string.search_result_verse,
                        new Object[] {chapterVerse[0], chapterVerse[1]});
                    TextView textView = (TextView) view;
                    textView.setText(string);
                    return true;
                }

                if (columnIndex == cursor.getColumnIndexOrThrow(Provider.COLUMN_UNFORMATTED)) {
                    String context = cursor.getString(columnIndex);
                    context = context.replaceAll("「", "“").replaceAll("」", "”");
                    context = context.replaceAll("『", "‘").replaceAll("』", "’");
                    ((TextView)view).setText(context);
                    return true;
                }
                return false;
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    showVerse(String.valueOf(id));
                } catch (Exception e) {
                }
            }
        });
    }

    private boolean showVerse(String id) {
        if (id == null) {
            return false;
        }
        Uri uri = Provider.CONTENT_URI_VERSE.buildUpon().appendEncodedPath(id).fragment(version).build();
        Cursor verseCursor = getContentResolver().query(uri, null, null, null, null);

        String book = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK));
        String verse = verseCursor.getString(verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE));
        int[] chapterVerse = bible.getChapterVerse(verse);
        verseCursor.close();

        Intent intent = new Intent(getApplicationContext(), Chapter.class);
        ArrayList<OsisItem> items = new ArrayList<OsisItem>();
        Log.d(TAG, String.format("book: %s, chapter: %d, verse: %d", book, chapterVerse[0], chapterVerse[1]));
        items.add(new OsisItem(book, chapterVerse[0], chapterVerse[1]));
        intent.putParcelableArrayListExtra("osiss", items);
        startActivity(intent);

        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(new Intent(getApplicationContext(), Search.class));
        return false;
    }

    public String getQueryBooks(String osisfrom, String osisto) {
        int frombook = -1;
        int tobook = -1;
        if (osisfrom != null && !osisfrom.equals("")) {
            frombook = bible.getPosition(Bible.TYPE.OSIS, osisfrom);
        }
        if (osisto != null && !osisto.equals("")) {
            tobook = bible.getPosition(Bible.TYPE.OSIS, osisto);
        }
        if (frombook == -1) {
            frombook = 0;
        }
        if (tobook == -1) {
            tobook = bible.getCount(Bible.TYPE.OSIS) - 1;
        }
        if (tobook < frombook) {
            int swap = frombook;
            frombook = tobook;
            tobook = swap;
        }
        String queryBooks = String.format("'%s'", bible.get(Bible.TYPE.OSIS, frombook));
        for (int i = frombook + 1; i <= tobook; i++) {
            queryBooks += String.format(", '%s'", bible.get(Bible.TYPE.OSIS, i));
        }
        humanfrom = bible.get(Bible.TYPE.HUMAN, frombook);
        humanto = bible.get(Bible.TYPE.HUMAN, tobook);
        return queryBooks;
    }

}
