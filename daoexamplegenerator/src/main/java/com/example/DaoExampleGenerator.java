package com.example;

import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Property;
import de.greenrobot.daogenerator.Schema;
import de.greenrobot.daogenerator.ToMany;

public class DaoExampleGenerator {
    static volatile int value = 0;
    public static void main(String[] args) throws Exception {
        // 正如你所见的，你创建了一个用于添加实体（Entity）的模式（Schema）对象。
        // 两个参数分别代表：数据库版本号与自动生成代码的包路径。
        //Schema schema = new Schema(1, "me.itangqi.greendao");
//      当然，如果你愿意，你也可以分别指定生成的 Bean 与 DAO 类所在的目录，只要如下所示：
//      Schema schema = new Schema(1, "me.itangqi.bean");
//      schema.setDefaultJavaPackageDao("me.itangqi.dao");

        // 模式（Schema）同时也拥有两个默认的 flags，分别用来标示 entity 是否是 activie 以及是否使用 keep sections。
        // schema2.enableActiveEntitiesByDefault();
        // schema2.enableKeepSectionsByDefault();

        // 一旦你拥有了一个 Schema 对象后，你便可以使用它添加实体（Entities）了。
        /*addNote(schema);*/
        /*addNote(schema);
        addCustomerOrder(schema);
        addUser(schema);*/

        // 最后我们将使用 DAOGenerator 类的 generateAll() 方法自动生成代码，此处你需要根据自己的情况更改输出目录（既之前创建的 java-gen)。
        // 其实，输出目录的路径可以在 build.gradle 中设置，有兴趣的朋友可以自行搜索，这里就不再详解。
        //new DaoGenerator().generateAll(schema, "D:/demo/greenDao-master-demo/app/src/main/java-gen");
        Runnable runnable1 = new Runnable() {
            @Override
            public void run() {
                /*synchronized (Objects.class) {

                    for (int i=0; i<20; i++) {
                        //value = value + 1;
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        value++;
                        System.out.println("value is" + value);
                    }
                }*/
                /*synchronized (Object.class) {

                }*/




                /*for (int i=0; i<200; i++) {
                    //value = value + 1;
                    value++;
                    //System.out.println("value is" + value);
                }*/
            }
        };
        //Runnable runnable2 = new My
        new Thread(new MyRunnalbe("thread1  ")).start();
        new Thread(new MyRunnalbe("thread-2  ")).start();
        new Thread(new MyRunnalbe("thread--3  ")).start();
        new Thread(new MyRunnalbe("thread---4  ")).start();


        //Thread.sleep(5000);
        //System.out.println("value is" + value);

        /*ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < 10; i++) {
            executorService.execute(runnable1);
        }
        executorService.shutdown();

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(value);*/
    }
    static class MyRunnalbe implements Runnable {
        String name;
        public MyRunnalbe(String name) {
            this.name = name;
        }
        @Override
        public void run() {
            for (int i=0; i<20; i++) {

                value = i + 1;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.out.println(name + value);
            }
        }
    }
    private synchronized static void test() {
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param schema
     */
    private static void addNote(Schema schema) {
        // 一个实体（类）就关联到数据库中的一张表，此处表名为「Note」（既类名）
        /*Entity note = schema.addEntity("Note");
        // 你也可以重新给表命名
        // note.setTableName("NODE");

        // greenDAO 会自动根据实体类的属性值来创建表字段，并赋予默认值
        // 接下来你便可以设置表中的字段：
        note.addIdProperty();
        note.addStringProperty("text").notNull();
        // 与在 Java 中使用驼峰命名法不同，默认数据库中的命名是使用大写和下划线来分割单词的。
        // For example, a property called “creationDate” will become a database column “CREATION_DATE”.
        note.addStringProperty("comment");
        note.addDateProperty("date");
        note.addToMany()*/
        Entity note = schema.addEntity("Note");
        note.addIdProperty();
        note.addStringProperty("text").notNull();
        note.addStringProperty("comment");
        note.addDateProperty("date");

    }


    private static void addUser(Schema schema) {
        Entity user = schema.addEntity("User");
        user.setTableName("t_user");
        user.addIdProperty();
        user.addStringProperty("account").unique();
        user.addStringProperty("password");
        user.addDateProperty("birthday");
        user.addShortProperty("gender");
        user.addIntProperty("height");
        user.addFloatProperty("weight");
        user.addDateProperty("registerTime");
        user.implementsInterface("Jsonable<User>");
    }

    private static void addCustomerOrder(Schema schema) {
        Entity customer = schema.addEntity("Customer");
        customer.addIdProperty();
        customer.addStringProperty("name").notNull();

        Entity order = schema.addEntity("Order");
        order.setTableName("ORDERS"); // "ORDER" is a reserved keyword
        order.addIdProperty();
        Property orderDate = order.addDateProperty("date").getProperty();
        Property customerId = order.addLongProperty("customerId").notNull().getProperty();
        order.addToOne(customer, customerId);

        ToMany customerToOrders = customer.addToMany(order, customerId);
        customerToOrders.setName("ordersss");
        customerToOrders.orderAsc(orderDate);
    }

}
