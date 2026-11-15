import com.hydrogen.PageStep;

/** 检查列表分页在字号、像素密度与边界尺寸下的覆盖。 */
public class PageStepTest {
    public static void main(String[] args) {
        if (PageStep.distance(0,24,2)!=0 || PageStep.distance(1,24,2)!=0) throw new AssertionError();
        if (PageStep.distance(50,40,5)!=1) throw new AssertionError("小视口须逐步前进");
        int cases=0;
        for (int height: new int[]{320,640,1100,1600,2400}) {
            for (int line: new int[]{16,24,36,48,72,96}) {
                int step=PageStep.distance(height,line,2);
                if (step<=0 || step>height-line) throw new AssertionError("步长应留出至少一整行");
                boolean[] seen=new boolean[200];
                int position=0, max=200*line-height;
                for (int page=0;page<1000;page++) {
                    for(int row=0;row<200;row++) if(row*line>=position&&(row+1)*line<=position+height) seen[row]=true;
                    int next=Math.min(max,position+step);
                    if(next==position) break;
                    position=next;
                }
                for(boolean visible:seen) if(!visible) throw new AssertionError("行遗漏");
                cases++;
            }
        }
        System.out.println(cases+" 组列表字号/可读高度覆盖测试通过");
    }
}
