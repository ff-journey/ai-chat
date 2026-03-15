from torch import nn


class FeiyanModel(nn.Module):
    def __init__(self):
        super(FeiyanModel, self).__init__()
        self.layer1 = nn.Sequential(
            # 1*512*512
            nn.Conv2d(1, 16, kernel_size=11, padding=5),
            # 512*512
            nn.BatchNorm2d(16),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=4)
        )
        self.layer2 = nn.Sequential(
            # 128*128
            nn.Conv2d(16, 64, kernel_size=7, padding=3),
            # 128*128
            nn.BatchNorm2d(64),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=4)
        )
        self.layer3 = nn.Sequential(
            # 1*32*32
            nn.Conv2d(64, 96, kernel_size=1),
            # 32*32
            nn.BatchNorm2d(96),
            nn.ReLU(),
            nn.MaxPool2d(kernel_size=4)
        )
        self.layer4 = nn.Sequential(
            # 96*8*8
            nn.Flatten(),
            nn.ReLU(),
            nn.Linear(96*8*8, 3)
        )
    def forward(self, x):
        out = self.layer1(x)
        out = self.layer2(out)
        out = self.layer3(out)
        out = self.layer4(out)
        return out
