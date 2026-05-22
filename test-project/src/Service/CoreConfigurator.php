<?php

namespace App\Service;

use App\Trait\MetricsTrait;

class CoreConfigurator
{
    use MetricsTrait;

    private string $configPath;

    public function __construct(string $configPath)
    {
        $this->configPath = $configPath;
    }

    public function configure(): void
    {
        $metrics = $this->getMetrics();
    }

    public function getConfigPath(): string
    {
        return $this->configPath;
    }
}
