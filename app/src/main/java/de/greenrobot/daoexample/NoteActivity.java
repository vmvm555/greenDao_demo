/*
 * Copyright (C) 2011 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.greenrobot.daoexample;

import android.app.ListActivity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

import de.greenrobot.dao.async.AsyncOperation;
import de.greenrobot.dao.async.AsyncOperationListener;
import de.greenrobot.dao.async.AsyncSession;
import de.greenrobot.daoexample.DaoMaster.DevOpenHelper;

public class NoteActivity extends ListActivity {

    private SQLiteDatabase db;

    private EditText editText;

    private DaoMaster daoMaster;
    private DaoSession daoSession;
    private NoteDao noteDao;

    private Cursor cursor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        //创建表代码已经自动生成了
        DevOpenHelper helper = new DaoMaster.DevOpenHelper(this, "notes-db", null);
        db = helper.getWritableDatabase();
        daoMaster = new DaoMaster(db);
        daoSession = daoMaster.newSession();
        //daoSession.insert(new Note());
        noteDao = daoSession.getNoteDao();
        /**
         * 可以理解forCurrentThread方法是用来保险的,防止多线程使用不对应的Query,每个线程有一个Query,避免用错
         * 使用forCurrentThread可以保证用的肯定是当前线程生产的Query
         */

        noteDao.queryRawCreateListArgs(null, null).forCurrentThread().list();
        AsyncSession asyncSession = daoSession.startAsyncSession();
        AsyncOperation operation = asyncSession.callInTx(null);

        asyncSession.runInTx(new Runnable() {
            @Override
            public void run() {

            }
        });
        //如果有查询逻辑的业务需求的话,并且希望使用事务,应该使用callInTx方法,这个方法会有返回值的
        //希望修改ui的话,使用setListenerMainThread这个回调
        asyncSession.setListenerMainThread(new AsyncOperationListener() {
            @Override
            public void onAsyncOperationCompleted(AsyncOperation operation) {

            }
        });
        asyncSession.callInTx(new Callable<List<Note>>() {

            @Override
            public List<Note> call() throws Exception {
                return  noteDao.queryRawCreateListArgs(null, null).forCurrentThread().list();
            }
        });
        //noteDao.queryRaw();



        //List<Note> list = daoSession.loadAll(Note.class);
        long start = SystemClock.elapsedRealtime();
        /*daoSession.runInTx(new Runnable() {
            @Override
            public void run() {
                //List<Note> list = daoSession.loadAll(Note.class);
                noteDao.loadAll();
            }
        });*/
        noteDao.loadAll();
        //daoSession.insert(new Note((long)1, "text" + 1, null, null));
        //34292行数据,使用FastCursor时候,3340, 3349, 3378, 3309, 3477毫秒,使用android官方的Cursor时,3835,3929,3986,3902,3992 毫秒
        //平均一下,使用FastCursor时候是3370毫秒,使用android官方Cursor时候是3928,差值为550毫秒综上所述,使用FastCursor能够使用时间减少15%左右
        Toast.makeText(this.getApplicationContext(), (SystemClock.elapsedRealtime() - start) + "", Toast.LENGTH_LONG).show();
        /*SystemClock.elapsedRealtime();
        SystemClock.elapsedRealtimeNanos();*/


        daoSession.runInTx(new Runnable() {
            @Override
            public void run() {
                for(int i=0; i<34292; i++) {
                    //daoSession.insert(new Note((long)i, "text" + 1, null, null));
                    noteDao.insert(new Note((long)i, "text" + 1, null, null));
                }
            }
        });
        Toast.makeText(this.getApplicationContext(), (System.currentTimeMillis() - start) + "", Toast.LENGTH_LONG).show();
        /*for(int i=0; i<150000; i++) {
            daoSession.insert(new Note((long)i, "text" + i, null, null));
            noteDao.insert()
        }*/
        System.out.println(db.isDbLockedByCurrentThread());
        System.out.println(db.isOpen());


        /*String textColumn = NoteDao.Properties.Text.columnName;
        String orderBy = textColumn + " COLLATE LOCALIZED ASC";
        cursor = db.query(noteDao.getTablename(), noteDao.getAllColumns(), null, null, null, null, orderBy);
        String[] from = {textColumn, NoteDao.Properties.Comment.columnName};
        int[] to = {android.R.id.text1, android.R.id.text2};

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, cursor, from,
                to);
        setListAdapter(adapter);*/

        editText = (EditText) findViewById(R.id.editTextNote);
        addUiListeners();

        /*daoSession.clear();


        Query<Note> ll = noteDao.queryRawCreate(null, null);
        ll.forCurrentThread();
        noteDao.queryRawCreateListArgs(null, null);
        //.list();
        noteDao.queryRaw(null, null);*/


        //daoSession.startAsyncSession().queryList(noteDao.queryRawCreate(null, null));
    }

    protected void addUiListeners() {
        editText.setOnEditorActionListener(new OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addNote();
                    return true;
                }
                return false;
            }
        });

        final View button = findViewById(R.id.buttonAdd);
        button.setEnabled(false);
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean enable = s.length() != 0;
                button.setEnabled(enable);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void onMyButtonClick(View view) {
        addNote();
    }

    private void addNote() {
        String noteText = editText.getText().toString();
        editText.setText("");

        final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        String comment = "Added on " + df.format(new Date());
        final Note note = new Note(null, noteText, comment, new Date());
        /*new Thread(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        noteDao.insert(note);
                    }
                }).start();
            }
        }).start();*/
        /*new Thread(new Runnable() {
            @Override
            public void run() {
                noteDao.insert(note);
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(7000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                noteDao.insert(note);
            }
        }).start();*/

        Log.d("DaoExample", "Inserted new note, ID: " + note.getId());
        System.out.println(db.isDbLockedByCurrentThread());
        //cursor.requery();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        noteDao.deleteByKey(id);
        Log.d("DaoExample", "Deleted note, ID: " + id);
        cursor.requery();
    }


    public static void main(String[] args) {
       /* FacadeRoom room = new FacadeRoom();
        room.CreateRoom();*/
        final BlockingQueue<String> queue = new ArrayBlockingQueue<String>(100);
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(10000);
                    queue.add("222");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }).start();
        try {
            queue.take();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}