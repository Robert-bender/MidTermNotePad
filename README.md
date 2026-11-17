# NotePad
项目来自于官方早期的数据库操作的基本教程。
更多的解析参考[Android Sample--NotePad解析](https://blog.csdn.net/llfjfz/article/details/67638499)

# 编译时遇到的问题
时代更迭，随着Gradle版本的升级，jcenter()方法已经在Gradle 9.0 中被彻底移除。

解决方法:
在build.gradle中将jcenter()全部替换成mavenCentral()即可，一共需要替换两处。
![img.png](img.png)![img_1.png](img_1.png)

# 本次更新实现的功能
1.NoteList界面中笔记条目增加时间戳显示

2.添加笔记查询功能（根据标题或内容查询）

# 有待补充和优化的
测试了一下已有的功能，没什么问题，就是在主页（NoteList界面）删除某一条笔记时会卡顿，耗时比较长。

还有复制粘贴笔记功能，目前点paste只能新建一个笔记，无法复制已有的笔记，不知道是不是我的打开方式不对。
