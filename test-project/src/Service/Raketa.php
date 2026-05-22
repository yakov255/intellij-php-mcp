<?php

namespace App\Service;

use App\Trait\MetricsTrait;

class Raketa
{
    use MetricsTrait;

    private string $name;

    public function __construct(string $name)
    {
        $this->name = $name;
    }

    public function doWork(): void
    {
        $metrics = $this->getMetrics();
    }

    public function getName(): string
    {
        return $this->name;
    }
}
