> 本文详细介绍四种事务隔离级别，并通过举例的方式说明不同的级别能解决什么样的读现象。并且介绍了在关系型数据库中不同的隔离级别的实现原理。

在DBMS中，**[事务](http://www.hollischuang.com/archives/898)保证了一个操作序列可以全部都执行或者全部都不执行（原子性）**，从一个状态转变到另外一个状态（一致性）。由于事务满足久性。所以一旦事务被提交之后，数据就能够被持久化下来，又因为事务是满足隔离性的，所以，当多个事务同时处理同一个数据的时候，多个事务直接是互不影响的，所以，在多个事务并发操作的过程中，如果控制不好隔离级别，就有可能产生[脏读](http://www.hollischuang.com/archives/900)、[不可重复读](http://www.hollischuang.com/archives/900)或者[幻读](http://www.hollischuang.com/archives/900)等读现象。

在数据库事务的[ACID](http://www.hollischuang.com/archives/898)四个属性中，隔离性是一个最常放松的一个。可以在数据操作过程中利用数据库的锁机制或者多版本并发控制机制获取更高的隔离等级。但是，随着数据库隔离级别的提高，数据的并发能力也会有所下降。所以，如何在并发性和隔离性之间做一个很好的权衡就成了一个至关重要的问题。

在软件开发中，几乎每类这样的问题都会有多种最佳实践来供我们参考，很多DBMS定义了多个不同的“事务隔离等级”来控制[锁](http://www.hollischuang.com/archives/909)的程度和并发能力。

ANSI/ISO SQL定义的标准隔离级别有四种，从高到底依次为：可序列化(Serializable)、可重复读(Repeatable reads)、提交读(Read committed)、未提交读(Read uncommitted)。

下面将依次介绍这四种事务隔离级别的概念、用法以及解决了哪些问题（读现象）

## 1. 未提交读(Read uncommitted)

未提交读(READ UNCOMMITTED)是最低的隔离级别。通过名字我们就可以知道，在这种事务隔离级别下，一个事务可以读到另外一个事务未提交的数据。

### 未提交读的数据库锁情况（实现原理）

> 事务在读数据的时候并未对数据加锁。
>
> 务在修改数据的时候只对数据增加[行级](http://www.hollischuang.com/archives/914)[共享锁](http://www.hollischuang.com/archives/923)。

现象：

> 事务1读取某行记录时，事务2也能对这行记录进行读取、更新（因为事务一并未对数据增加任何锁）
>
> 当事务2对该记录进行更新时，事务1再次读取该记录，能读到事务2对该记录的修改版本（因为事务二只增加了共享读锁，事务一可以再增加共享读锁读取数据），即使该修改尚未被提交。
>
> 事务1更新某行记录时，事务2不能对这行记录做更新，直到事务1结束。（因为事务一对数据增加了共享读锁，事务二不能增加[排他写锁](http://www.hollischuang.com/archives/923)进行数据的修改）

### 举例

下面还是借用我在[数据库的读现象浅析](http://www.hollischuang.com/archives/900)一文中举的例子来说明在未提交读的隔离级别中两个事务之间的隔离情况。

| 事务一                                                       | 事务二                                                       |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `/* Query 1 */` `SELECT age FROM users WHERE id = 1;` `/* will read 20 */` |                                                              |
|                                                              | `/* Query 2 */`  `UPDATE users SET age = 21 WHERE id = 1;` `/* No commit here */` |
| `/* Query 1 */` `SELECT age FROM users WHERE id = 1;` `/* will read 21 */` |                                                              |
|                                                              | `ROLLBACK;` `/* lock-based DIRTY READ */`                    |

事务一共查询了两次，在两次查询的过程中，事务二对数据进行了修改，并未提交（commit）。但是事务一的第二次查询查到了事务二的修改结果。在数据库的读现象浅析中我们介绍过，这种现象我们称之为[脏读](http://www.hollischuang.com/archives/900)。

所以，**未提交读会导致脏读**



**示例2**

在事务A和事务B同时执行时可能会出现如下场景：

| 时间 | 事务A（存款）          | 事务B（取款）              |
| ---- | ---------------------- | -------------------------- |
| T1   | 开始事务               | ——                         |
| T2   | ——                     | 开始事务                   |
| T3   | ——                     | 查询余额（1000元）         |
| T4   | ——                     | 取出1000元（余额0元）      |
| T5   | 查询余额（0元）        | ——                         |
| T6   | ——                     | 撤销事务（余额恢复1000元） |
| T7   | 存入500元（余额500元） | ——                         |
| T8   | 提交事务               | ——                         |

余额应该为1500元才对。请看T5时间点，事务A此时查询的余额为0，这个数据就是**脏数据**，他是事务B造成的，很明显是事务没有进行隔离造成的。

## 2. 提交读(Read committed)

提交读(READ COMMITTED)也可以翻译成读已提交，通过名字也可以分析出，在一个事务修改数据过程中，如果事务还没提交，其他事务不能读该数据。

### 提交读的数据库锁情况

> 事务对当前被读取的数据加 行级共享锁（当读到时才加锁），一旦读完该行，立即释放该行级共享锁；
>
> 事务在更新某数据的瞬间（就是发生更新的瞬间），必须先对其加 行级排他锁，直到事务结束才释放。

现象：

> 事务1在读取某行记录的整个过程中，事务2都可以对该行记录进行读取（因为事务一对该行记录增加行级共享锁的情况下，事务二同样可以对该数据增加共享锁来读数据。）。
>
> 事务1读取某行的一瞬间，事务2不能修改该行数据，但是，只要事务1读取完改行数据，事务2就可以对该行数据进行修改。（事务一在读取的一瞬间会对数据增加共享锁，任何其他事务都不能对该行数据增加排他锁。但是事务一只要读完该行数据，就会释放行级共享锁，一旦锁释放，事务二就可以对数据增加排他锁并修改数据）
>
> 事务1更新某行记录时，事务2不能对这行记录做更新，直到事务1结束。（事务一在更新数据的时候，会对该行数据增加排他锁，知道事务结束才会释放锁，所以，在事务二没有提交之前，事务一都能不对数据增加共享锁进行数据的读取。**所以，提交读可以解决脏读的现象**）

### 举例

| 事务一                                                       | 事务二                                                       |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `/* Query 1 */` `SELECT * FROM users WHERE id = 1;`          |                                                              |
|                                                              | `/* Query 2 */`  `UPDATE users SET age = 21 WHERE id = 1;` `COMMIT;` `/* in multiversion concurrencycontrol, or lock-based READ COMMITTED */` |
| `/* Query 1 */` `SELECT * FROM users WHERE id = 1;` `COMMIT; ` `/*lock-based REPEATABLE READ */` |                                                              |

在提交读隔离级别中，在事务二提交之前，事务一不能读取数据。只有在事务二提交之后，事务一才能读数据。

但是从上面的例子中我们也看到，事务一两次读取的结果并不一致，**所以提交读不能解决不可重复读的读现象。**

简而言之，提交读这种隔离级别保证了读到的任何数据都是提交的数据，避免了脏读(dirty reads)。但是不保证事务重新读的时候能读到相同的数据，因为在每次数据读完之后其他事务可以修改刚才读到的数据。



**示例2**

| 时间 | 事务A（存款）      | 事务B（取款）         |
| ---- | ------------------ | --------------------- |
| T1   | 开始事务           | ——                    |
| T2   | ——                 | 开始事务              |
| T3   | ——                 | 查询余额（1000元）    |
| T4   | 查询余额（1000元） | ——                    |
| T5   | ——                 | 取出1000元（余额0元） |
| T6   | ——                 | 提交事务              |
| T7   | 查询余额（0元）    | ——                    |
| T8   | 提交事务           | ——                    |

事务A其实除了查询两次以外，其它什么事情都没做，结果钱就从1000编程0了，这就是不可重复读的问题。

## 3. 可重复读(Repeatable reads)

可重复读(REPEATABLE READS),由于提交读隔离级别会产生不可重复读的读现象。所以，比提交读更高一个级别的隔离级别就可以解决不可重复读的问题。这种隔离级别就叫可重复读（这名字起的是不是很任性！！）

### 可重复读的数据库锁情况

> 事务在读取某数据的瞬间（就是开始读取的瞬间），必须先对其加 行级共享锁，直到事务结束才释放；
>
> 事务在更新某数据的瞬间（就是发生更新的瞬间），必须先对其加 行级排他锁，直到事务结束才释放。

现象

> 事务1在读取某行记录的整个过程中，事务2都可以对该行记录进行读取（因为事务一对该行记录增加行级共享锁的情况下，事务二同样可以对该数据增加共享锁来读数据。）。
>
> 事务1在读取某行记录的整个过程中，事务2都不能修改该行数据（事务一在读取的整个过程会对数据增加共享锁，直到事务提交才会释放锁，所以整个过程中，任何其他事务都不能对该行数据增加排他锁。**所以，可重复读能够解决不可重复读的读现象**）
>
> 事务1更新某行记录时，事务2不能对这行记录做更新，直到事务1结束。（事务一在更新数据的时候，会对该行数据增加排他锁，知道事务结束才会释放锁，所以，在事务二没有提交之前，事务一都能不对数据增加共享锁进行数据的读取。**所以，提交读可以解决脏读的现象**）

### 举例

| 事务一                                                       | 事务二                                                       |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `/* Query 1 */` `SELECT * FROM users WHERE id = 1;` `COMMIT;` |                                                              |
|                                                              | `/* Query 2 */`  `UPDATE users SET age = 21 WHERE id = 1;` `COMMIT;` `/* in multiversion concurrencycontrol, or lock-based READ COMMITTED */` |

在上面的例子中，只有在事务一提交之后，事务二才能更改该行数据。所以，只要在事务一从开始到结束的这段时间内，无论他读取该行数据多少次，结果都是一样的。

从上面的例子中我们可以得到结论：可重复读隔离级别可以解决不可重复读的读现象。**但是可重复读这种隔离级别中，还有另外一种读现象他解决不了，那就是幻读。**看下面的例子：

| 事务一                                                       | 事务二                                                       |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `/* Query 1 */` `SELECT * FROM usersWHERE age BETWEEN 10 AND 30;` |                                                              |
|                                                              | `/* Query 2 */`  `INSERT INTO users VALUES ( 3, 'Bob', 27 );` `COMMIT;` |
| `/* Query 1 */` `SELECT * FROM usersWHERE age BETWEEN 10 AND 30;` |                                                              |

上面的两个事务执行情况及现象如下：

1.事务一的第一次查询条件是`age BETWEEN 10 AND 30;`如果这是有十条记录符合条件。这时，他会给符合条件的这十条记录增加行级共享锁。任何其他事务无法更改这十条记录。

2.事务二执行一条sql语句，语句的内容是向表中插入一条数据。因为此时没有任何事务对表增加[表级锁](http://www.hollischuang.com/archives/914)，所以，该操作可以顺利执行。

3.事务一再次执行`SELECT * FROM users WHERE age BETWEEN 10 AND 30;`时，结果返回的记录变成了十一条，比刚刚增加了一条，增加的这条正是事务二刚刚插入的那条。

所以，事务一的两次范围查询结果并不相同。这也就是我们提到的幻读。



| 时间 | 事务A（统计总存款）   | 事务B（存款） |
| ---: | --------------------- | ------------- |
|   T1 | 开始事务              | ——            |
|   T2 | ——                    | 开始事务      |
|   T3 | 统计总存款（10000元） | ——            |
|   T4 | ——                    | 存入100元     |
|   T5 | ——                    | 提交事务      |
|   T6 | 提交总存款（10100）   | ——            |
|   T7 | 提交事务              | ——            |

银行工作人员在一个事务中多次统计总存款时看到结果不一样。如果要解决幻读，那只能使用顺序读了。

## 4. 可序列化(Serializable)

可序列化(Serializable)是最高的隔离级别，前面提到的所有的隔离级别都无法解决的幻读，在可序列化的隔离级别中可以解决。

我们说过，产生幻读的原因是事务一在进行范围查询的时候没有增加范围锁(range-locks：给SELECT 的查询中使用一个“WHERE”子句描述范围加锁），所以导致幻读。

### 可序列化的数据库锁情况

> 事务在读取数据时，必须先对其加 表级共享锁 ，直到事务结束才释放；
>
> 事务在更新数据时，必须先对其加 表级排他锁 ，直到事务结束才释放。

现象

> 事务1正在读取A表中的记录时，则事务2也能读取A表，但不能对A表做更新、新增、删除，直到事务1结束。(因为事务一对表增加了表级共享锁，其他事务只能增加共享锁读取数据，不能进行其他任何操作）
>
> 事务1正在更新A表中的记录时，则事务2不能读取A表的任意记录，更不可能对A表做更新、新增、删除，直到事务1结束。（事务一对表增加了表级排他锁，其他事务不能对表增加共享锁或排他锁，也就无法进行任何操作）

虽然可序列化解决了脏读、不可重复读、幻读等读现象。但是序列化事务会产生以下效果：

1.无法读取其它事务已修改但未提交的记录。

2.在当前事务完成之前，其它事务不能修改目前事务已读取的记录。

3.在当前事务完成之前，其它事务所插入的新记录，其索引键值不能在当前事务的任何语句所读取的索引键范围中。

------

四种事务隔离级别从隔离程度上越来越高，但同时在并发性上也就越来越低。之所以有这么几种隔离级别，就是为了方便开发人员在开发过程中根据业务需要选择最合适的隔离级别。



## 5. 事务隔离级别对比

| 事务隔离级别                 | 脏    读 | 不可重复读 | 幻    读 |
| ---------------------------- | -------- | ---------- | -------- |
| 读未提及（READ_UNCOMMITTED） | 允许     | 允许       | 允许     |
| 读已提交（READ_COMMITTED）   | 禁止     | 允许       | 允许     |
| 可重复读（REPEATABLE_READ）  | 禁止     | 禁止       | 允许     |
| 顺序读（SERIALIZABLE）       | 禁止     | 禁止       | 禁止     |

## 参考资料

[维基百科-事务隔离](https://zh.wikipedia.org/wiki/%E4%BA%8B%E5%8B%99%E9%9A%94%E9%9B%A2)

[数据库隔离级别 及 其实现原理](http://my.oschina.net/HuQingmiao/blog/518101)




