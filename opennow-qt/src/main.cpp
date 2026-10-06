#include "app/ApplicationStartup.h"

#ifdef _WIN32
// Hybrid-graphics laptops run unknown programs on the integrated GPU. These
// exports ask the NVIDIA Optimus and AMD PowerXpress drivers to run Cloudlight
// on the discrete GPU instead, as GeForce NOW's own client does; on the
// integrated GPU the stream decodes far below its frame rate. The drivers
// only read them from the executable itself, so they must stay in this file.
extern "C" {
__declspec(dllexport) unsigned long NvOptimusEnablement = 0x00000001;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}
#endif

int main(int argc, char *argv[])
{
    return runApplication(argc, argv);
}
