import torch, torch.nn as nn, torch.nn.functional as F, numpy as np, glob, os
from PIL import Image
class RDB(nn.Module):
    def __init__(s,nf=64,gc=32):
        super().__init__()
        s.conv1=nn.Conv2d(nf,gc,3,1,1); s.conv2=nn.Conv2d(nf+gc,gc,3,1,1); s.conv3=nn.Conv2d(nf+2*gc,gc,3,1,1)
        s.conv4=nn.Conv2d(nf+3*gc,gc,3,1,1); s.conv5=nn.Conv2d(nf+4*gc,nf,3,1,1); s.l=nn.LeakyReLU(0.2,True)
    def forward(s,x):
        x1=s.l(s.conv1(x)); x2=s.l(s.conv2(torch.cat((x,x1),1))); x3=s.l(s.conv3(torch.cat((x,x1,x2),1)))
        x4=s.l(s.conv4(torch.cat((x,x1,x2,x3),1))); x5=s.conv5(torch.cat((x,x1,x2,x3,x4),1)); return x5*0.2+x
class RRDB(nn.Module):
    def __init__(s,nf=64,gc=32):
        super().__init__(); s.rdb1=RDB(nf,gc); s.rdb2=RDB(nf,gc); s.rdb3=RDB(nf,gc)
    def forward(s,x): return s.rdb3(s.rdb2(s.rdb1(x)))*0.2+x
class RRDBNet(nn.Module):
    def __init__(s,nf=64,nb=23,gc=32):
        super().__init__()
        s.conv_first=nn.Conv2d(3,nf,3,1,1); s.body=nn.Sequential(*[RRDB(nf,gc) for _ in range(nb)])
        s.conv_body=nn.Conv2d(nf,nf,3,1,1); s.conv_up1=nn.Conv2d(nf,nf,3,1,1); s.conv_up2=nn.Conv2d(nf,nf,3,1,1)
        s.conv_hr=nn.Conv2d(nf,nf,3,1,1); s.conv_last=nn.Conv2d(nf,3,3,1,1); s.l=nn.LeakyReLU(0.2,True)
    def forward(s,x):
        f=s.conv_first(x); f=f+s.conv_body(s.body(f))
        f=s.l(s.conv_up1(F.interpolate(f,scale_factor=2,mode='nearest')))
        f=s.l(s.conv_up2(F.interpolate(f,scale_factor=2,mode='nearest')))
        return s.conv_last(s.l(s.conv_hr(f)))
m=RRDBNet(); sd=torch.load('RealESRGAN_x4plus.pth',map_location='cpu')
m.load_state_dict(sd.get('params_ema',sd)); m.eval(); torch.set_num_threads(os.cpu_count())
os.makedirs('out',exist_ok=True)
T,P=192,12
for f in sorted(glob.glob('src/*.png')):
    a=np.asarray(Image.open(f).convert('RGB'),np.float32)/255.
    x=torch.from_numpy(a).permute(2,0,1)[None]; _,_,H,W=x.shape
    out=torch.zeros(1,3,H*4,W*4)
    with torch.no_grad():
        for y in range(0,H,T):
            for xx in range(0,W,T):
                y0,x0=max(0,y-P),max(0,xx-P); y1,x1=min(H,y+T+P),min(W,xx+T+P)
                o=m(x[:,:,y0:y1,x0:x1])
                oy,ox=(y-y0)*4,(xx-x0)*4; h,w=min(T,H-y)*4,min(T,W-xx)*4
                out[:,:,y*4:y*4+h,xx*4:xx*4+w]=o[:,:,oy:oy+h,ox:ox+w]
    r=(out[0].permute(1,2,0).clamp(0,1).numpy()*255).round().astype(np.uint8)
    Image.fromarray(r).save('out/'+os.path.basename(f)); print('done',f,r.shape,flush=True)
