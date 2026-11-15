import com.hydrogen.ImageTiles;
import java.util.Random;

/** 验证长图、原尺寸宽图和区域解码的覆盖与内存上限。 */
public class ImageTilesTest {
    private static void check(int width, int height, int vw, int vh, boolean original) {
        ImageTiles tiles = new ImageTiles(width, height, vw, vh, original, 20);
        int[] first = tiles.region(0);
        int[] last = tiles.region(tiles.size()-1);
        if (first[0]!=0 || first[1]!=0 || last[2]!=width || last[3]!=height) throw new AssertionError("图片边界遗漏");
        for (int i=0;i<tiles.size();i++) {
            int[] r=tiles.region(i);
            if (r[0]<0 || r[1]<0 || r[2]>width || r[3]>height || r[0]>=r[2] || r[1]>=r[3]) throw new AssertionError();
            if (i%tiles.columns>0 && tiles.region(i-1)[2]<r[0]) throw new AssertionError("列间有缺口");
            if (i>=tiles.columns && tiles.region(i-tiles.columns)[3]<r[1]) throw new AssertionError("行间有缺口");
        }
        int sample=tiles.sampleSize();
        if ((long)(tiles.tileWidth/sample)*(tiles.tileHeight/sample)>4_000_000) throw new AssertionError("区域位图超限");
        if (!original && tiles.columns!=1) throw new AssertionError("适应宽度须一列");
    }
    public static void main(String[] args) {
        check(1600,100000,1264,1400,false);
        check(6000,6000,1264,1400,true);
        check(300,100,1264,1400,false);
        check(16000,32000,4000,4000,false);
        Random random=new Random(7);
        for(int i=0;i<100;i++) check(1+random.nextInt(10000),1+random.nextInt(40000),500+random.nextInt(1500),700+random.nextInt(2000),random.nextBoolean());
        System.out.println("104 组长图/宽图分段覆盖、比例与区域解码内存边界通过");
    }
}
